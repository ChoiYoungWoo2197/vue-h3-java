package com.h3.h3_java.batch.master;

import com.h3.h3_java.media.naver.NaverApiClient;
import com.h3.h3_java.media.naver.dto.NaverAccountDto;
import com.h3.h3_java.raw.mongo.AccountMongoService;
import com.h3.h3_java.raw.mongo.NaverMasterMongoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class NaverAdDetailJob {

    private final AccountMongoService accountMongo;
    private final NaverMasterMongoService mongoService;

    private static final ExecutorService FETCH_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();
    private static final String IMG_HOST = "https://searchad-phinf.pstatic.net";

    private static final Map<String, Integer> INSPECT_STATUS = Map.of(
        "UNDER_REVIEW", 10, "APPROVED", 20, "ELIGIBLE", 30, "PENDING", 40, "DENIED", 50
    );

    public void collect() {
        List<NaverAccountDto> accounts = accountMongo.findNaverAccountDtos();
        Set<String> seen = new HashSet<>();
        for (NaverAccountDto account : accounts) {
            if ("admin".equals(account.getUserId())) continue;
            String cid = account.getAccountNaverCustomer();
            if (cid == null || cid.isBlank() || !seen.add(cid)) continue;
            collectForAccount(account);
        }
    }

    public boolean collectForUserId(String userId) {
        NaverAccountDto target = accountMongo.findNaverAccountDtos().stream()
            .filter(a -> userId.equals(a.getUserId()))
            .findFirst().orElse(null);
        if (target == null) return false;
        collectForAccount(target);
        return true;
    }

    private void collectForAccount(NaverAccountDto account) {
        String customerId = account.getAccountNaverCustomer();
        String userId     = account.getUserId();
        log.info("[AD_DETAIL][START] userId={} customerId={}", userId, customerId);

        NaverApiClient client = new NaverApiClient(
            account.getAccountNaverAccess(),
            account.getAccountNaverSecret(),
            customerId
        );

        processKeywords(customerId, client);
        processAds(customerId, client);
        processShoppingProducts(customerId, client);

        log.info("[AD_DETAIL][END] userId={} customerId={}", userId, customerId);
    }

    @SuppressWarnings("unchecked")
    private void processKeywords(String customerId, NaverApiClient client) {
        List<String> kwIds = mongoService.selectKeywordIds(customerId);
        if (kwIds.isEmpty()) {
            log.info("[AD_DETAIL][KW] customerId={} no keywords", customerId);
            return;
        }
        log.info("[AD_DETAIL][KW] customerId={} keywords={}", customerId, kwIds.size());

        ConcurrentHashMap<String, Map<String, Object>> kwCache = new ConcurrentHashMap<>();

        CompletableFuture.allOf(
            partition(kwIds, 100).stream()
                .map(batch -> CompletableFuture.runAsync(() -> {
                    Map<String, String> params = new LinkedHashMap<>();
                    params.put("ids", String.join(",", batch));
                    List<Map<String, Object>> results = client.getList("/ncc/keywords", params);
                    if (results != null) {
                        for (Map<String, Object> d : results) {
                            String id = String.valueOf(d.getOrDefault("nccKeywordId", ""));
                            if (!id.isEmpty()) kwCache.put(id, d);
                        }
                    }
                }, FETCH_EXECUTOR))
                .toArray(CompletableFuture[]::new)
        ).join();

        Map<String, Map<String, Object>> kwUpdateMap = new LinkedHashMap<>();
        for (String kwid : kwIds) {
            kwUpdateMap.put(kwid, buildKwUpdate(kwCache.getOrDefault(kwid, new HashMap<>())));
        }
        mongoService.bulkUpdateKeywordDetails(customerId, kwUpdateMap);
        log.info("[AD_DETAIL][KW] customerId={} updated={}", customerId, kwIds.size());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildKwUpdate(Map<String, Object> detail) {
        Map<String, Object> update = new HashMap<>();
        int bidamount = 0, inspect = 0, usinggrp = 0, qigrade = 0;
        String purl = "", murl = "";

        if (!detail.isEmpty()) {
            bidamount = parseInt(detail.get("bidAmt"));
            Map<String, Object> links = (Map<String, Object>) detail.get("links");
            if (links != null) {
                Map<String, Object> pc = (Map<String, Object>) links.get("pc");
                Map<String, Object> mo = (Map<String, Object>) links.get("mobile");
                if (pc != null) purl = String.valueOf(pc.getOrDefault("final", ""));
                if (mo != null) murl = String.valueOf(mo.getOrDefault("final", ""));
            }
            inspect  = INSPECT_STATUS.getOrDefault(String.valueOf(detail.getOrDefault("inspectStatus", "")), 0);
            usinggrp = "true".equals(String.valueOf(detail.getOrDefault("useGroupBidAmt", "false"))) ? 1 : 0;
            Map<String, Object> qi = (Map<String, Object>) detail.get("nccQi");
            if (qi != null) qigrade = parseInt(qi.get("qiGrade"));
        }

        update.put("bidamount",      bidamount);
        update.put("plandingurl",    purl);
        update.put("mlandingurl",    murl);
        update.put("kinspectstatus", inspect);
        update.put("usinggrpbid",    usinggrp);
        update.put("qigrade",        qigrade);
        return update;
    }

    @SuppressWarnings("unchecked")
    private void processAds(String customerId, NaverApiClient client) {
        List<Document> adDocs = mongoService.selectAdDocs(customerId);
        if (adDocs.isEmpty()) {
            log.info("[AD_DETAIL][AD] customerId={} no ads", customerId);
            return;
        }

        List<String> adIds    = adDocs.stream().map(d -> d.getString("adid")).filter(Objects::nonNull).collect(Collectors.toList());
        List<String> groupIds = adDocs.stream().map(d -> d.getString("groupid")).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        log.info("[AD_DETAIL][AD] customerId={} ads={} groups={}", customerId, adIds.size(), groupIds.size());

        ConcurrentHashMap<String, Map<String, Object>> adCache  = new ConcurrentHashMap<>();
        ConcurrentHashMap<String, String>              imgCache = new ConcurrentHashMap<>(); // extid -> imageUrl

        // /ncc/ads 배치 병렬 조회
        CompletableFuture<Void> adFetch = CompletableFuture.allOf(
            partition(adIds, 100).stream()
                .map(batch -> CompletableFuture.runAsync(() -> {
                    Map<String, String> params = new LinkedHashMap<>();
                    params.put("ids", String.join(",", batch));
                    List<Map<String, Object>> results = client.getList("/ncc/ads", params);
                    if (results != null) {
                        for (Map<String, Object> d : results) {
                            String id = String.valueOf(d.getOrDefault("nccAdId", ""));
                            if (!id.isEmpty()) adCache.put(id, d);
                        }
                    }
                }, FETCH_EXECUTOR))
                .toArray(CompletableFuture[]::new)
        );

        // 그룹 이미지 확장소재 병렬 조회
        List<Document> extDocs = mongoService.selectGroupExtIds(groupIds);
        List<String> extIds = extDocs.stream().map(d -> d.getString("extid")).filter(Objects::nonNull).distinct().collect(Collectors.toList());

        CompletableFuture<Void> extFetch = CompletableFuture.allOf(
            extIds.stream()
                .map(eid -> CompletableFuture.runAsync(() -> {
                    Map<String, Object> ext = client.get("/ncc/ad-extensions/" + eid);
                    if (ext != null) {
                        Map<String, Object> adExt = (Map<String, Object>) ext.get("adExtension");
                        if (adExt != null) {
                            String path = (String) adExt.get("imagePath");
                            if (path != null && !path.isEmpty()) imgCache.put(eid, IMG_HOST + path);
                        }
                    }
                }, FETCH_EXECUTOR))
                .toArray(CompletableFuture[]::new)
        );

        CompletableFuture.allOf(adFetch, extFetch).join();

        // 그룹별 이미지 URL 목록 빌드 (최대 3개)
        Map<String, List<String>> groupImgMap = new HashMap<>();
        for (Document er : extDocs) {
            String ownerid = er.getString("ownerid");
            String url     = imgCache.get(er.getString("extid"));
            if (url != null) {
                List<String> imgs = groupImgMap.computeIfAbsent(ownerid, k -> new ArrayList<>());
                if (imgs.size() < 3) imgs.add(url);
            }
        }

        for (Document doc : adDocs) {
            String adid    = doc.getString("adid");
            String groupid = doc.getString("groupid");
            Map<String, Object> detail = adCache.getOrDefault(adid, new HashMap<>());
            List<String> imgs = groupImgMap.getOrDefault(groupid, new ArrayList<>());
            mongoService.updateAdDetail(customerId, adid, buildAdUpdate(detail, imgs));
        }
        log.info("[AD_DETAIL][AD] customerId={} updated={}", customerId, adIds.size());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildAdUpdate(Map<String, Object> detail, List<String> imgs) {
        Map<String, Object> update = new HashMap<>();
        String subject = "", desc = "";

        List<Map<String, Object>> assets = (List<Map<String, Object>>) detail.get("assets");
        if (assets != null) {
            List<String> h = new ArrayList<>(), d = new ArrayList<>();
            for (Map<String, Object> asset : assets) {
                Map<String, Object> ad = (Map<String, Object>) asset.get("assetData");
                if (ad != null) {
                    String text = String.valueOf(ad.getOrDefault("text", ""));
                    if ("HEADLINE".equals(asset.get("linkType")))    h.add(text);
                    if ("DESCRIPTION".equals(asset.get("linkType"))) d.add(text);
                }
            }
            if (!h.isEmpty()) subject = String.join("|", h);
            if (!d.isEmpty()) desc    = String.join("|", d);
        }
        Map<String, Object> ad = (Map<String, Object>) detail.get("ad");
        if (ad != null) {
            if (ad.get("headline")    != null) subject = (String) ad.get("headline");
            if (ad.get("description") != null) desc    = (String) ad.get("description");
        }

        update.put("subject",     subject);
        update.put("description", desc);
        update.put("imgurl1",     imgs.size() > 0 ? imgs.get(0) : "");
        update.put("imgurl2",     imgs.size() > 1 ? imgs.get(1) : "");
        update.put("imgurl3",     imgs.size() > 2 ? imgs.get(2) : "");
        return update;
    }

    @SuppressWarnings("unchecked")
    private void processShoppingProducts(String customerId, NaverApiClient client) {
        List<String> spIds = mongoService.selectShoppingProductIds(customerId);
        if (spIds.isEmpty()) {
            log.info("[AD_DETAIL][SP] customerId={} no products", customerId);
            return;
        }
        log.info("[AD_DETAIL][SP] customerId={} products={}", customerId, spIds.size());

        ConcurrentHashMap<String, Map<String, Object>> spCache = new ConcurrentHashMap<>();

        CompletableFuture.allOf(
            partition(spIds, 100).stream()
                .map(batch -> CompletableFuture.runAsync(() -> {
                    Map<String, String> params = new LinkedHashMap<>();
                    params.put("ids", String.join(",", batch));
                    List<Map<String, Object>> results = client.getList("/ncc/ads", params);
                    if (results != null) {
                        for (Map<String, Object> d : results) {
                            String id = String.valueOf(d.getOrDefault("nccAdId", ""));
                            if (!id.isEmpty()) spCache.put(id, d);
                        }
                    }
                }, FETCH_EXECUTOR))
                .toArray(CompletableFuture[]::new)
        ).join();

        for (String adid : spIds) {
            mongoService.updateShoppingProductDetail(customerId, adid, buildSpUpdate(spCache.getOrDefault(adid, new HashMap<>())));
        }
        log.info("[AD_DETAIL][SP] customerId={} updated={}", customerId, spIds.size());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildSpUpdate(Map<String, Object> detail) {
        Map<String, Object> update = new HashMap<>();
        String pname = "", productname = "", cnameofmall = "", brand = "", maker = "", imgurl = "";
        int bid = 0, qigrade = 0, deliveryfee = 0;

        Map<String, Object> ad   = (Map<String, Object>) detail.get("ad");
        if (ad != null) pname = String.valueOf(ad.getOrDefault("productName", ""));
        Map<String, Object> attr = (Map<String, Object>) detail.get("adAttr");
        if (attr != null) bid = parseInt(attr.get("bidAmt"));
        Map<String, Object> qi   = (Map<String, Object>) detail.get("nccQi");
        if (qi != null) qigrade = parseInt(qi.get("qiGrade"));
        Map<String, Object> ref  = (Map<String, Object>) detail.get("referenceData");
        if (ref != null) {
            deliveryfee = parseInt(ref.get("dvlryFeeCont"));
            cnameofmall = String.valueOf(ref.getOrDefault("fullMallCatNm", ""));
            brand       = String.valueOf(ref.getOrDefault("brand", ""));
            maker       = String.valueOf(ref.getOrDefault("maker", ""));
            imgurl      = String.valueOf(ref.getOrDefault("imageUrl", ""));
            productname = String.valueOf(ref.getOrDefault("productName", ""));
        }

        update.put("pname",       pname);
        update.put("bid",         bid);
        update.put("qigrade",     qigrade);
        update.put("deliveryfee", deliveryfee);
        update.put("cnameofmall", cnameofmall);
        update.put("brand",       brand);
        update.put("maker",       maker);
        update.put("imageurl",    imgurl);
        update.put("productname", productname);
        return update;
    }

    private int parseInt(Object val) {
        if (val == null) return 0;
        String s = String.valueOf(val).trim();
        if (s.isEmpty()) return 0;
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 0; }
    }

    private <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size)
            result.add(list.subList(i, Math.min(i + size, list.size())));
        return result;
    }
}
