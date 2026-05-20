package com.h3.h3_java.batch.master;

import com.h3.h3_java.media.naver.NaverApiClient;
import com.h3.h3_java.media.naver.NaverTsvParser;
import com.h3.h3_java.media.naver.dto.NaverAccountDto;
import com.h3.h3_java.media.naver.dto.NaverDeltaDto;
import com.h3.h3_java.media.naver.mapper.NaverMasterReportMapper;
import com.h3.h3_java.raw.mongo.NaverMasterMongoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class NaverMasterReportJob {

    private final NaverMasterReportMapper mapper;
    private final NaverMasterMongoService mongoService;

    private static final String[] SPECS = {
        "Campaign", "CampaignBudget", "Adgroup", "AdgroupBudget",
        "AdExtension", "Keyword", "Ad", "RsaAd", "ContentsAd", "ShoppingProduct"
    };

    private static final Map<String, Integer> INSPECT_STATUS = Map.of(
        "UNDER_REVIEW", 10, "APPROVED", 20, "ELIGIBLE", 30, "PENDING", 40, "DENIED", 50
    );

    // 배치 fetch 전용 가상 스레드 실행기 (Java 21)
    private static final ExecutorService FETCH_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    // 계정별 캐시 - 계정 간 격리, 스레드 안전
    private static class AccountContext {
        final ConcurrentHashMap<String, Map<String, Object>> kwCache    = new ConcurrentHashMap<>();
        final ConcurrentHashMap<String, Map<String, Object>> adCache    = new ConcurrentHashMap<>();
        final ConcurrentHashMap<String, Map<String, Object>> adextCache = new ConcurrentHashMap<>();
    }

    private record SpecResult(String spec, String reportId, String downloadUrl, boolean shouldDownload) {}

    public void collect() {
        List<NaverAccountDto> accounts = mapper.selectNaverAccounts();
        Set<String> seen = new HashSet<>();
        for (NaverAccountDto account : accounts) {
            if ("admin".equals(account.getUserId())) continue;
            String cid = account.getAccountNaverCustomer();
            if (cid == null || cid.isBlank() || !seen.add(cid)) continue;
            collectForAccount(account, false);
        }
    }

    public void collectForce() {
        List<NaverAccountDto> accounts = mapper.selectNaverAccounts();
        Set<String> seen = new HashSet<>();
        for (NaverAccountDto account : accounts) {
            if ("admin".equals(account.getUserId())) continue;
            String cid = account.getAccountNaverCustomer();
            if (cid == null || cid.isBlank() || !seen.add(cid)) continue;
            collectForAccount(account, true);
        }
    }

    public boolean collectForUserId(String userId, boolean force) {
        NaverAccountDto target = mapper.selectNaverAccounts().stream()
            .filter(a -> userId.equals(a.getUserId()))
            .findFirst().orElse(null);
        if (target == null) return false;
        collectForAccount(target, force);
        return true;
    }

    private void collectForAccount(NaverAccountDto account, boolean force) {
        String customerId = account.getAccountNaverCustomer();
        if (customerId == null || customerId.isBlank()) {
            log.warn("[NAVER][SKIP] userId={} customerId 없음", account.getUserId());
            return;
        }
        log.info("[NAVER][START] userId={} customerId={}", account.getUserId(), customerId);

        NaverApiClient client = new NaverApiClient(
            account.getAccountNaverAccess(),
            account.getAccountNaverSecret(),
            customerId
        );
        AccountContext ctx = new AccountContext();

        // spec 수만큼 스레드 - submit/poll 동시 진행
        ExecutorService executor = Executors.newFixedThreadPool(SPECS.length);
        try {
            // spec별 독립 파이프라인: 폴링 완료 즉시 다운로드 + 처리 (다른 spec 대기 없음)
            List<CompletableFuture<Void>> futures = Arrays.stream(SPECS)
                .map(spec -> CompletableFuture
                    .supplyAsync(() -> submitAndPoll(account, spec, client, force), executor)
                    .thenAcceptAsync(result -> {
                        if (result != null && result.shouldDownload() && result.downloadUrl() != null) {
                            downloadAndProcess(result, account, client, ctx);
                        }
                    }, executor))
                .collect(Collectors.toList());

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        } finally {
            executor.shutdown();
        }

        log.info("[NAVER][END] userId={} customerId={}", account.getUserId(), customerId);
    }

    private SpecResult submitAndPoll(NaverAccountDto account, String spec, NaverApiClient client, boolean force) {
        String customerId = account.getAccountNaverCustomer();
        String userId     = account.getUserId();

        NaverDeltaDto delta = mapper.selectNaverDelta(customerId, spec, userId);
        String deltaValue   = (delta != null) ? delta.getDelta() : null;

        Map<String, Object> reqBody = new HashMap<>();
        reqBody.put("item", spec);
        // force=true 이면 fromTime 생략 → 전체 수집
        if (!force && deltaValue != null && !deltaValue.isEmpty()) {
            reqBody.put("fromTime", deltaValue);
        }

        Map<String, Object> res = client.post("/master-reports", reqBody);
        if (res == null || !res.containsKey("id")) {
            log.warn("[NAVER][{}][{}] create fail", customerId, spec);
            return null;
        }

        String reportId = String.valueOf(res.get("id"));
        String status   = String.valueOf(res.get("status"));
        log.info("[NAVER][{}][{}] submitted id={}", customerId, spec, reportId);

        Map<String, Object> checkResult = res;
        int waited = 0;
        while (("REGIST".equals(status) || "RUNNING".equals(status)) && waited < 300) {
            sleep(5000);
            waited += 5;
            checkResult = client.get("/master-reports/" + reportId);
            status = checkResult != null ? String.valueOf(checkResult.get("status")) : "ERROR";
        }

        if ("NONE".equals(status)) {
            log.info("[NAVER][{}][{}] no data", customerId, spec);
            client.delete("/master-reports/" + reportId);
            return null;
        }

        if (!"BUILT".equals(status)) {
            log.warn("[NAVER][{}][{}] build fail status={}", customerId, spec, status);
            return null;
        }

        String downloadUrl = (String) checkResult.get("downloadUrl");
        String updateTime  = (String) checkResult.get("updateTime");
        String oldJobId    = (delta != null) ? delta.getJobid() : null;
        boolean shouldDownload = false;

        int cnt = mapper.countNaverDelta(customerId, spec, userId);
        if (cnt == 0) {
            NaverDeltaDto d = new NaverDeltaDto();
            d.setDeltakey(customerId); d.setDelta(updateTime);
            d.setName(spec); d.setJobid(reportId); d.setUserid(userId);
            mapper.insertNaverDelta(d);
            shouldDownload = true;
        } else if (!Objects.equals(updateTime, deltaValue)) {
            NaverDeltaDto d = new NaverDeltaDto();
            d.setDeltakey(customerId); d.setDelta(updateTime);
            d.setName(spec); d.setJobid(reportId); d.setUserid(userId);
            mapper.updateNaverDelta(d);
            if (oldJobId != null) client.delete("/master-reports/" + oldJobId);
            shouldDownload = true;
        } else if (force) {
            // force 모드: 변경 없어도 강제 다운로드
            shouldDownload = true;
        } else {
            log.info("[NAVER][{}][{}] no changes, skip", customerId, spec);
            client.delete("/master-reports/" + reportId);
        }

        return new SpecResult(spec, reportId, downloadUrl, shouldDownload);
    }

    private void downloadAndProcess(SpecResult result, NaverAccountDto account,
                                    NaverApiClient client, AccountContext ctx) {
        String customerId = account.getAccountNaverCustomer();
        String userId     = account.getUserId();

        byte[] tsvBytes = client.download(result.downloadUrl());
        if (tsvBytes == null || tsvBytes.length == 0) {
            mapper.updateNaverDeltaFail(customerId, result.spec(), userId);
            log.warn("[NAVER][{}][{}] download failed", customerId, result.spec());
            client.delete("/master-reports/" + result.reportId());
            return;
        }

        List<Map<String, String>> rows = NaverTsvParser.parse(tsvBytes, result.spec());
        processSpecRows(result.spec(), rows, client, ctx);
        log.info("[NAVER][{}][{}] rows={} upserted", customerId, result.spec(), rows.size());
        client.delete("/master-reports/" + result.reportId());
    }

    private void processSpecRows(String spec, List<Map<String, String>> rows,
                                  NaverApiClient client, AccountContext ctx) {
        if (rows.isEmpty()) return;
        switch (spec) {
            case "Campaign"        -> rows.forEach(r -> mongoService.upsertCampaign(toIntRow(r, "onoff")));
            case "CampaignBudget"  -> rows.forEach(r -> mongoService.upsertCampaignBudget(toIntRow(r, "usingdailybudget")));
            case "Adgroup"         -> rows.forEach(r -> mongoService.upsertAdgroup(toIntRow(r, "bidamount", "onoff")));
            case "AdgroupBudget"   -> rows.forEach(r -> mongoService.upsertAdgroupBudget(toIntRow(r, "usingdailybudget")));
            case "AdExtension"     -> rows.forEach(r -> mongoService.upsertAdExtension(toIntRow(r, "type", "tmonday", "ttuesday", "twednesday", "tthursday", "tfriday", "tsaturday", "tsunday", "onoff", "inspect")));
            case "Keyword"         -> processKeywords(rows, client, ctx);
            case "Ad"              -> processAds(rows, 1, client, ctx);
            case "RsaAd"           -> processAds(rows, 2, client, ctx);
            case "ContentsAd"      -> processAds(rows, 3, client, ctx);
            case "ShoppingProduct" -> processShoppingProducts(rows, client, ctx);
        }
    }

    private void processKeywords(List<Map<String, String>> rows, NaverApiClient client, AccountContext ctx) {
        List<String> kwids = rows.stream()
            .map(r -> r.get("kwid"))
            .filter(id -> id != null && !id.isEmpty())
            .distinct()
            .collect(Collectors.toList());

        // [TEST] 키워드 API 호출 주석처리 - 성능 측정용
//        List<CompletableFuture<Void>> futures = partition(kwids, 100).stream()
//            .map(batch -> CompletableFuture.runAsync(() -> {
//                Map<String, String> params = new LinkedHashMap<>();
//                params.put("ids", String.join(",", batch));
//                List<Map<String, Object>> results = client.getList("/ncc/keywords", params);
//                if (results != null) {
//                    for (Map<String, Object> kw : results) {
//                        String id = String.valueOf(kw.getOrDefault("nccKeywordId", ""));
//                        if (!id.isEmpty()) ctx.kwCache.putIfAbsent(id, kw);
//                    }
//                }
//            }, FETCH_EXECUTOR))
//            .collect(Collectors.toList());
//        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        for (List<Map<String, String>> chunk : partition(rows, 200)) {
            List<Map<String, Object>> upsertRows = new ArrayList<>();
            for (Map<String, String> r : chunk) {
                if (r.get("kwid") == null || r.get("kwid").isEmpty()) continue;
                upsertRows.add(buildKeywordRow(r, ctx.kwCache.getOrDefault(r.get("kwid"), new HashMap<>())));
            }
            if (!upsertRows.isEmpty()) {
                mongoService.upsertKeywords(upsertRows);
                log.info("[KEYWORD][CHUNK_SAVE] saved={}", upsertRows.size());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildKeywordRow(Map<String, String> base, Map<String, Object> detail) {
        Map<String, Object> row = new HashMap<>();
        row.put("advkey", base.get("advkey"));
        row.put("adgroupid", base.get("adgroupid"));
        row.put("kwid", base.get("kwid"));
        row.put("kword", base.get("kword"));
        row.put("onoff", parseInt(base.get("onoff")));
        row.put("kwtype", 0);

        int bidamount = 0; String purl = "", murl = ""; int inspect = 0, usinggrp = 0, qigrade = 0;

        if (!detail.isEmpty()) {
            bidamount = parseInt(detail.get("bidAmt"));
            Map<String, Object> links = (Map<String, Object>) detail.get("links");
            if (links != null) {
                Map<String, Object> pc = (Map<String, Object>) links.get("pc");
                Map<String, Object> mo = (Map<String, Object>) links.get("mobile");
                if (pc != null) purl = String.valueOf(pc.getOrDefault("final", ""));
                if (mo != null) murl = String.valueOf(mo.getOrDefault("final", ""));
            }
            inspect   = INSPECT_STATUS.getOrDefault(String.valueOf(detail.getOrDefault("inspectStatus", "")), 0);
            usinggrp  = "true".equals(String.valueOf(detail.getOrDefault("useGroupBidAmt", "false"))) ? 1 : 0;
            Map<String, Object> qi = (Map<String, Object>) detail.get("nccQi");
            if (qi != null) qigrade = parseInt(qi.get("qiGrade"));
        }

        row.put("bidamount", bidamount); row.put("plandingurl", purl); row.put("mlandingurl", murl);
        row.put("kinspectstatus", inspect); row.put("usinggrpbid", usinggrp); row.put("qigrade", qigrade);
        return row;
    }

    @SuppressWarnings("unchecked")
    private void processAds(List<Map<String, String>> rows, int adType,
                             NaverApiClient client, AccountContext ctx) {
        // [TEST] 소재/반응소재 API 호출 주석처리 - 성능 측정용
        List<String> adids = rows.stream()
            .map(r -> r.get("adid"))
            .filter(id -> id != null && !id.isEmpty())
            .distinct()
            .collect(Collectors.toList());

//        List<CompletableFuture<Void>> adFutures = partition(adids, 100).stream()
//            .map(batch -> CompletableFuture.runAsync(() -> {
//                Map<String, String> params = new LinkedHashMap<>();
//                params.put("ids", String.join(",", batch));
//                List<Map<String, Object>> results = client.getList("/ncc/ads", params);
//                if (results != null) {
//                    for (Map<String, Object> ad : results) {
//                        String id = String.valueOf(ad.getOrDefault("nccAdId", ""));
//                        if (!id.isEmpty()) ctx.adCache.putIfAbsent(id, ad);
//                    }
//                }
//            }, FETCH_EXECUTOR))
//            .collect(Collectors.toList());
//        CompletableFuture.allOf(adFutures.toArray(new CompletableFuture[0])).join();

        List<String> groupids = rows.stream().map(r -> r.get("groupid"))
            .filter(id -> id != null && !id.isEmpty()).distinct().collect(Collectors.toList());

        Map<String, List<String>> groupExtMap = new HashMap<>();
        if (!groupids.isEmpty()) {
            for (org.bson.Document er : mongoService.selectGroupExtIds(groupids)) {
                groupExtMap.computeIfAbsent(er.getString("ownerid"), k -> new ArrayList<>())
                           .add(er.getString("extid"));
            }
        }

        // [TEST] ad-extension API 호출 주석처리 - 성능 측정용
        List<String> extIds = groupExtMap.values().stream()
            .flatMap(Collection::stream).distinct().collect(Collectors.toList());
//        List<CompletableFuture<Void>> extFutures = extIds.stream()
//            .map(eid -> CompletableFuture.runAsync(() ->
//                ctx.adextCache.computeIfAbsent(eid, id -> {
//                    Map<String, Object> d = client.get("/ncc/ad-extensions/" + id);
//                    return d != null ? d : new HashMap<>();
//                }), FETCH_EXECUTOR))
//            .collect(Collectors.toList());
//        CompletableFuture.allOf(extFutures.toArray(new CompletableFuture[0])).join();

        String imgHost = "https://searchad-phinf.pstatic.net";
        Map<String, List<String>> groupImgMap = new HashMap<>();
        for (Map.Entry<String, List<String>> e : groupExtMap.entrySet()) {
            List<String> imgs = new ArrayList<>();
            for (String eid : e.getValue()) {
                Map<String, Object> ext = (Map<String, Object>) ctx.adextCache
                    .getOrDefault(eid, new HashMap<>()).get("adExtension");
                if (ext != null) {
                    String path = (String) ext.get("imagePath");
                    if (path != null && !path.isEmpty()) imgs.add(imgHost + path);
                    if (imgs.size() >= 3) break;
                }
            }
            groupImgMap.put(e.getKey(), imgs);
        }

        for (Map<String, String> r : rows) {
            mongoService.upsertAd(buildAdRow(r, adType,
                ctx.adCache.getOrDefault(r.get("adid"), new HashMap<>()),
                groupImgMap.getOrDefault(r.get("groupid"), new ArrayList<>())));
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildAdRow(Map<String, String> base, int adType,
                                           Map<String, Object> detail, List<String> imgs) {
        Map<String, Object> row = new HashMap<>(base);
        row.put("type", adType);
        row.put("creativeinspect", parseInt(base.get("creativeinspect")));
        row.put("onoff", parseInt(base.get("onoff")));

        String subject = base.getOrDefault("subject", "");
        String desc    = base.getOrDefault("description", "");

        if (!detail.isEmpty()) {
            List<Map<String, Object>> assets = (List<Map<String, Object>>) detail.get("assets");
            if (assets != null) {
                List<String> h = new ArrayList<>(), d = new ArrayList<>();
                for (Map<String, Object> asset : assets) {
                    Map<String, Object> ad = (Map<String, Object>) asset.get("assetData");
                    if (ad != null) {
                        String text = String.valueOf(ad.getOrDefault("text", ""));
                        if ("HEADLINE".equals(asset.get("linkType")))     h.add(text);
                        if ("DESCRIPTION".equals(asset.get("linkType")))  d.add(text);
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
        }

        row.put("subject", subject); row.put("description", desc);
        row.put("imgurl1", imgs.size() > 0 ? imgs.get(0) : "");
        row.put("imgurl2", imgs.size() > 1 ? imgs.get(1) : "");
        row.put("imgurl3", imgs.size() > 2 ? imgs.get(2) : "");
        return row;
    }

    @SuppressWarnings("unchecked")
    private void processShoppingProducts(List<Map<String, String>> rows,
                                         NaverApiClient client, AccountContext ctx) {
        // 100개씩 배치 + 병렬 fetch
        List<String> adids = rows.stream()
            .map(r -> r.get("adid"))
            .filter(id -> id != null && !id.isEmpty())
            .distinct()
            .collect(Collectors.toList());

        List<CompletableFuture<Void>> adFutures = partition(adids, 100).stream()
            .map(batch -> CompletableFuture.runAsync(() -> {
                Map<String, String> params = new LinkedHashMap<>();
                params.put("ids", String.join(",", batch));
                List<Map<String, Object>> results = client.getList("/ncc/ads", params);
                if (results != null) {
                    for (Map<String, Object> ad : results) {
                        String id = String.valueOf(ad.getOrDefault("nccAdId", ""));
                        if (!id.isEmpty()) ctx.adCache.putIfAbsent(id, ad);
                    }
                }
            }, FETCH_EXECUTOR))
            .collect(Collectors.toList());
        CompletableFuture.allOf(adFutures.toArray(new CompletableFuture[0])).join();

        for (Map<String, String> r : rows) {
            Map<String, Object> detail = ctx.adCache.getOrDefault(r.get("adid"), new HashMap<>());
            Map<String, Object> row = new HashMap<>(r);
            row.put("onoff",    parseInt(r.get("onoff")));
            row.put("usingbid", "true".equals(r.get("usingbid")) ? 1 : 0);

            String pname = "", productname = "", cnameofmall = "", brand = "", maker = "", imgurl = "";
            int bid = 0, qigrade = 0, deliveryfee = 0;

            if (!detail.isEmpty()) {
                Map<String, Object> ad   = (Map<String, Object>) detail.get("ad");
                if (ad != null) pname = String.valueOf(ad.getOrDefault("productName", ""));
                Map<String, Object> attr = (Map<String, Object>) detail.get("adAttr");
                if (attr != null) bid = parseInt(attr.get("bidAmt"));
                Map<String, Object> qi   = (Map<String, Object>) detail.get("nccQi");
                if (qi != null) qigrade = parseInt(qi.get("qiGrade"));
                Map<String, Object> ref  = (Map<String, Object>) detail.get("referenceData");
                if (ref != null) {
                    deliveryfee  = parseInt(ref.get("dvlryFeeCont"));
                    cnameofmall  = String.valueOf(ref.getOrDefault("fullMallCatNm", ""));
                    brand        = String.valueOf(ref.getOrDefault("brand", ""));
                    maker        = String.valueOf(ref.getOrDefault("maker", ""));
                    imgurl       = String.valueOf(ref.getOrDefault("imageUrl", ""));
                    productname  = String.valueOf(ref.getOrDefault("productName", ""));
                }
            }

            row.put("pname", pname); row.put("bid", bid); row.put("qigrade", qigrade);
            row.put("deliveryfee", deliveryfee); row.put("cnameofmall", cnameofmall);
            row.put("brand", brand); row.put("maker", maker);
            row.put("imageurl", imgurl); row.put("productname", productname);
            mongoService.upsertShoppingProduct(row);
        }
    }

    private Map<String, Object> toIntRow(Map<String, String> row, String... intFields) {
        Map<String, Object> result = new HashMap<>(row);
        for (String f : intFields) result.put(f, parseInt(row.get(f)));
        return result;
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

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}