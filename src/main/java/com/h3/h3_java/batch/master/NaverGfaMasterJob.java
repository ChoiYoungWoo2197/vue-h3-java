package com.h3.h3_java.batch.master;

import com.h3.h3_java.batch.scheduler.NaverGfaTokenManager;
import com.h3.h3_java.media.naver.dto.NaverGfaAccountDto;
import com.h3.h3_java.media.naver.mapper.NaverGfaMapper;
import com.h3.h3_java.raw.mongo.NaverGfaMasterMongoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class NaverGfaMasterJob {

    private final NaverGfaMapper mapper;
    private final NaverGfaMasterMongoService mongoService;
    private final NaverGfaTokenManager tokenManager;

    private static final String GFA_BASE = "https://openapi.naver.com/v1/ad-api/1.1";

    private static final Map<String, Integer> TYPE_SET = Map.of(
        "conversion", 0, "web_site_traffic", 1, "install_app", 2, "watch_video", 3,
        "catalog", 4, "shopping", 5, "lead", 6, "pmax", 7
    );
    private static final Map<String, Integer> BIDGOAL_SET = Map.of(
        "max_click", 0, "max_conv", 1, "none", 2
    );
    private static final Map<String, Integer> BIDTYPE_SET = Map.of(
        "cpc", 0, "cpm", 1, "cpv", 2
    );
    private static final Map<String, Integer> BUDGETTYPE_SET = Map.of(
        "daily", 0, "total", 1
    );
    private static final Map<String, Integer> PGROUPS_SET = Map.ofEntries(
        Map.entry("band", 0), Map.entry("f_banner", 1), Map.entry("f_smartchannel", 2),
        Map.entry("m_banner", 3), Map.entry("m_feed", 4), Map.entry("m_main", 5),
        Map.entry("m_smartchannel", 6), Map.entry("nw_banner", 7), Map.entry("nw_smartchannel", 8),
        Map.entry("n_communication", 9), Map.entry("n_instream", 10), Map.entry("n_shopping", 11)
    );
    private static final Map<String, Integer> CTYPE_SET = Map.of(
        "single_image", 0, "multiple_image", 1, "single_video", 2,
        "image_banner", 3, "catalog", 4, "composition", 5
    );
    private static final Map<String, Integer> STATUS_SET = Map.of(
        "pending", 0, "reject", 1, "accept", 2,
        "pending_in_operation", 3, "reject_in_operation", 4, "deleted", 5
    );

    public void collect() {
        List<NaverGfaAccountDto> accounts = mapper.selectGfaAccounts();
        for (NaverGfaAccountDto account : accounts) {
            collectForAccount(account);
        }
    }

    public boolean collectForUserId(String userId) {
        NaverGfaAccountDto account = mapper.selectGfaAccounts().stream()
            .filter(a -> userId.equals(a.getUserId()))
            .findFirst().orElse(null);
        if (account == null) return false;
        collectForAccount(account);
        return true;
    }

    private void collectForAccount(NaverGfaAccountDto account) {
        String advkey = account.getAccountGfa();
        String token = tokenManager.getAccessToken();
        String managerNo = tokenManager.getAccessManagerAccountNo();

        if (token == null || managerNo == null) {
            log.warn("[GFA][MASTER][SKIP] 토큰 없음 userId={}", account.getUserId());
            return;
        }

        log.info("[GFA][MASTER][START] userId={} advkey={}", account.getUserId(), advkey);

        List<CompletableFuture<Void>> futures = List.of(
            CompletableFuture.runAsync(() -> collectCampaigns(advkey, token, managerNo)),
            CompletableFuture.runAsync(() -> collectAdgroups(advkey, token, managerNo)),
            CompletableFuture.runAsync(() -> collectAds(advkey, token, managerNo))
        );
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        log.info("[GFA][MASTER][END] userId={} advkey={}", account.getUserId(), advkey);
    }

    private void collectCampaigns(String advkey, String token, String managerNo) {
        String url = GFA_BASE + "/adAccounts/" + advkey + "/campaigns";
        List<Map<String, Object>> items = fetchAllPages(url, 10, token, managerNo);
        for (Map<String, Object> r : items) {
            Map<String, Object> row = new HashMap<>();
            row.put("advkey", advkey);
            row.put("bid", toInt(r.get("brandNo")));
            row.put("cid", String.valueOf(r.get("no")));
            row.put("cname", String.valueOf(r.getOrDefault("name", "")));
            row.put("onoff", Boolean.TRUE.equals(r.get("activated")) ? 1 : 0);
            row.put("type", TYPE_SET.getOrDefault(String.valueOf(r.getOrDefault("objective", "")).toLowerCase(), 0));
            mongoService.upsertGfaCampaign(row);
        }
        log.info("[GFA][MASTER] campaign advkey={} count={}", advkey, items.size());
    }

    @SuppressWarnings("unchecked")
    private void collectAdgroups(String advkey, String token, String managerNo) {
        String url = GFA_BASE + "/adAccounts/" + advkey + "/adSets";
        List<Map<String, Object>> items = fetchAllPages(url, 100, token, managerNo);
        for (Map<String, Object> r : items) {
            Map<String, Object> row = new HashMap<>();
            row.put("advkey", advkey);
            row.put("cid", String.valueOf(r.get("campaignNo")));
            row.put("gid", String.valueOf(r.get("no")));
            row.put("gname", String.valueOf(r.getOrDefault("name", "")));
            row.put("onoff", Boolean.TRUE.equals(r.get("activated")) ? 1 : 0);
            row.put("bidgoal", BIDGOAL_SET.getOrDefault(String.valueOf(r.getOrDefault("bidGoal", "")).toLowerCase(), 2));
            row.put("bidtype", BIDTYPE_SET.getOrDefault(String.valueOf(r.getOrDefault("bidType", "")).toLowerCase(), 0));
            row.put("budgettype", BUDGETTYPE_SET.getOrDefault(String.valueOf(r.getOrDefault("budgetType", "")).toLowerCase(), 0));
            row.put("budgetamount", toInt(r.get("budgetAmount")));
            row.put("bidprice", toInt(r.get("bidPrice")));
            int pgroups = 0;
            List<Map<String, Object>> pg = (List<Map<String, Object>>) r.get("placementGroups");
            if (pg != null && !pg.isEmpty()) {
                String code = String.valueOf(pg.get(0).getOrDefault("code", "")).toLowerCase();
                pgroups = PGROUPS_SET.getOrDefault(code, 0);
            }
            row.put("pgroups", pgroups);
            mongoService.upsertGfaAdgroup(row);
        }
        log.info("[GFA][MASTER] adgroup advkey={} count={}", advkey, items.size());
    }

    @SuppressWarnings("unchecked")
    private void collectAds(String advkey, String token, String managerNo) {
        String url = GFA_BASE + "/adAccounts/" + advkey + "/creatives";
        List<Map<String, Object>> items = fetchAllPages(url, 100, token, managerNo);
        for (Map<String, Object> r : items) {
            Map<String, Object> row = new HashMap<>();
            row.put("advkey", advkey);
            row.put("gid", String.valueOf(r.get("adSetNo")));
            row.put("aid", String.valueOf(r.get("no")));
            row.put("headline", String.valueOf(r.getOrDefault("name", "")));
            String purl = "";
            Map<String, Object> image = (Map<String, Object>) r.get("image");
            if (image != null) purl = String.valueOf(image.getOrDefault("imageUrl", ""));
            row.put("purl", purl);
            row.put("murl", String.valueOf(r.getOrDefault("linkUrl", "")));
            row.put("onoff", Boolean.TRUE.equals(r.get("activated")) ? 1 : 0);
            row.put("ctype", CTYPE_SET.getOrDefault(String.valueOf(r.getOrDefault("creativeType", "")).toLowerCase(), 0));
            row.put("status", STATUS_SET.getOrDefault(String.valueOf(r.getOrDefault("status", "")).toLowerCase(), 0));
            mongoService.upsertGfaAd(row);
        }
        log.info("[GFA][MASTER] ad advkey={} count={}", advkey, items.size());
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchAllPages(String baseUrl, int size, String token, String managerNo) {
        List<Map<String, Object>> all = new ArrayList<>();
        RestTemplate rt = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        headers.set("AccessManagerAccountNo", managerNo);
        HttpEntity<?> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map> res = rt.exchange(baseUrl + "?page=0&size=" + size, HttpMethod.GET, entity, Map.class);
            Map<String, Object> body = res.getBody();
            if (body == null) return all;

            List<Map<String, Object>> content = (List<Map<String, Object>>) body.get("content");
            int totalPages = toInt(body.get("totalPages"));
            if (content != null) all.addAll(content);

            for (int p = 1; p < totalPages; p++) {
                ResponseEntity<Map> pageRes = rt.exchange(baseUrl + "?page=" + p + "&size=" + size, HttpMethod.GET, entity, Map.class);
                Map<String, Object> pageBody = pageRes.getBody();
                if (pageBody != null) {
                    List<Map<String, Object>> pageContent = (List<Map<String, Object>>) pageBody.get("content");
                    if (pageContent != null) all.addAll(pageContent);
                }
            }
        } catch (Exception e) {
            log.error("[GFA][MASTER] API 오류 url={} error={}", baseUrl, e.getMessage());
        }
        return all;
    }

    private int toInt(Object val) {
        if (val == null) return 0;
        try { return ((Number) val).intValue(); } catch (Exception e) { return 0; }
    }
}
