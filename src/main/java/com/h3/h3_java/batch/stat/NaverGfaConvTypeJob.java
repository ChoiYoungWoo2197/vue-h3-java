package com.h3.h3_java.batch.stat;

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
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class NaverGfaConvTypeJob {

    private final NaverGfaMapper gfaMapper;
    private final NaverGfaMasterMongoService mongoService;
    private final NaverGfaTokenManager tokenManager;

    private static final String GFA_BASE = "https://openapi.naver.com/v1/ad-api/1.0";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final int RETRY_MAX = 5;

    // =====================================================================
    // Public Entry Points
    // =====================================================================

    public void collect() {
        List<NaverGfaAccountDto> accounts = gfaMapper.selectGfaAccounts();
        for (NaverGfaAccountDto acc : accounts) {
            collectAccount(acc, null, null);
        }
    }

    public boolean collectForUserId(String userId) {
        NaverGfaAccountDto acc = gfaMapper.selectGfaAccounts().stream()
            .filter(a -> userId.equals(a.getUserId()))
            .findFirst().orElse(null);
        if (acc == null) return false;
        collectAccount(acc, null, null);
        return true;
    }

    public void collectRange(String userId, String fromDate, String toDate) {
        NaverGfaAccountDto acc = gfaMapper.selectGfaAccounts().stream()
            .filter(a -> userId.equals(a.getUserId()))
            .findFirst().orElse(null);
        if (acc == null) return;
        collectAccount(acc, fromDate, toDate);
    }

    // =====================================================================
    // Account-level Collection
    // =====================================================================

    private void collectAccount(NaverGfaAccountDto acc, String fromDate, String toDate) {
        String advkey = acc.getAccountGfa();
        String token = tokenManager.getAccessToken();
        String managerNo = tokenManager.getAccessManagerAccountNo();

        if (token == null || managerNo == null) {
            log.warn("[GFA][CONV-TYPE][SKIP] 토큰 없음 userId={}", acc.getUserId());
            return;
        }

        List<String> dates = (fromDate != null && toDate != null)
            ? buildDateRange(fromDate, toDate)
            : buildAutoDates(advkey);

        for (String date : dates) {
            int cpSaved = collectCampaignConvType(advkey, token, managerNo, date);
            log.info("[GFA][CONV-TYPE-CAMPAIGN] advkey={} date={} saved={}", advkey, date, cpSaved);
            randomSleep(900, 1400);

            int agSaved = collectAdgroupConvType(advkey, token, managerNo, date);
            log.info("[GFA][CONV-TYPE-ADGROUP] advkey={} date={} saved={}", advkey, date, agSaved);
            randomSleep(1100, 1700);

            int adSaved = collectAdConvType(advkey, token, managerNo, date);
            log.info("[GFA][CONV-TYPE-AD] advkey={} date={} saved={}", advkey, date, adSaved);
            randomSleep(1300, 2000);
        }
    }

    // =====================================================================
    // Campaign ConvType
    // =====================================================================

    private int collectCampaignConvType(String advkey, String token, String managerNo, String date) {
        if (!LocalDate.parse(date, DATE_FMT).isBefore(LocalDate.now())) return 0;

        String baseUrl = GFA_BASE + "/adAccounts/" + advkey
            + "/performance/conversion/past/campaigns?startDate=" + date
            + "&endDate=" + date + "&limit=1000&timeUnit=daily";

        List<Map<String, Object>> rawRows = fetchAllPages(baseUrl, token, managerNo);

        Map<String, Map<String, Object>> bucket = new LinkedHashMap<>();
        for (Map<String, Object> r : rawRows) {
            String campaignId   = str(r.get("campaignNo"));
            String convTypeRaw  = str(r.get("convType"));
            String targetDate   = str(r.getOrDefault("targetDate", date));
            if (campaignId.isEmpty() || campaignId.equals("0") || convTypeRaw.isEmpty()) continue;

            String convTypeCode = normalizeConvTypeCode(convTypeRaw);
            String key = advkey + "|" + date + "|" + campaignId + "|" + convTypeCode;

            bucket.computeIfAbsent(key, k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("daily_advid",     advkey);
                m.put("daily_dt",        targetDate);
                m.put("campaign_id",     campaignId);
                m.put("conv_type_code",  convTypeCode);
                m.put("conv_type_name",  getConvTypeName(convTypeCode));
                m.put("conv_cnt",        0.0);
                m.put("conv_value",      0.0);
                return m;
            });

            Map<String, Object> entry = bucket.get(key);
            entry.put("conv_cnt",   toDouble(entry.get("conv_cnt"))   + toDouble(r.get("convCount")));
            entry.put("conv_value", toDouble(entry.get("conv_value")) + toDouble(r.get("convSales")));
        }

        if (bucket.isEmpty()) return 0;
        mongoService.bulkUpsertGfaCampaignConvType(new ArrayList<>(bucket.values()));
        return bucket.size();
    }

    // =====================================================================
    // Adgroup ConvType
    // =====================================================================

    private int collectAdgroupConvType(String advkey, String token, String managerNo, String date) {
        if (!LocalDate.parse(date, DATE_FMT).isBefore(LocalDate.now())) return 0;

        String baseUrl = GFA_BASE + "/adAccounts/" + advkey
            + "/performance/conversion/past/adSets?startDate=" + date
            + "&endDate=" + date + "&limit=1000&timeUnit=daily";

        List<Map<String, Object>> rawRows = fetchAllPages(baseUrl, token, managerNo);

        Map<String, Map<String, Object>> bucket = new LinkedHashMap<>();
        for (Map<String, Object> r : rawRows) {
            String campaignId   = str(r.get("campaignNo"));
            String adgroupId    = str(r.get("adSetNo"));
            String convTypeRaw  = str(r.get("convType"));
            String targetDate   = str(r.getOrDefault("targetDate", date));
            if (campaignId.isEmpty() || campaignId.equals("0")) continue;
            if (adgroupId.isEmpty()  || adgroupId.equals("0"))  continue;
            if (convTypeRaw.isEmpty()) continue;

            String convTypeCode = normalizeConvTypeCode(convTypeRaw);
            String key = advkey + "|" + date + "|" + campaignId + "|" + adgroupId + "|" + convTypeCode;

            bucket.computeIfAbsent(key, k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("daily_advid",     advkey);
                m.put("daily_dt",        targetDate);
                m.put("campaign_id",     campaignId);
                m.put("adgroup_id",      adgroupId);
                m.put("conv_type_code",  convTypeCode);
                m.put("conv_type_name",  getConvTypeName(convTypeCode));
                m.put("conv_cnt",        0.0);
                m.put("conv_value",      0.0);
                return m;
            });

            Map<String, Object> entry = bucket.get(key);
            entry.put("conv_cnt",   toDouble(entry.get("conv_cnt"))   + toDouble(r.get("convCount")));
            entry.put("conv_value", toDouble(entry.get("conv_value")) + toDouble(r.get("convSales")));
        }

        if (bucket.isEmpty()) return 0;
        mongoService.bulkUpsertGfaAdgroupConvType(new ArrayList<>(bucket.values()));
        return bucket.size();
    }

    // =====================================================================
    // Ad ConvType
    // =====================================================================

    private int collectAdConvType(String advkey, String token, String managerNo, String date) {
        if (!LocalDate.parse(date, DATE_FMT).isBefore(LocalDate.now())) return 0;

        String baseUrl = GFA_BASE + "/adAccounts/" + advkey
            + "/performance/conversion/past/creatives?startDate=" + date
            + "&endDate=" + date + "&limit=1000&timeUnit=daily";

        List<Map<String, Object>> rawRows = fetchAllPages(baseUrl, token, managerNo);

        Map<String, Map<String, Object>> bucket = new LinkedHashMap<>();
        for (Map<String, Object> r : rawRows) {
            String campaignId   = str(r.get("campaignNo"));
            String adgroupId    = str(r.get("adSetNo"));
            String adId         = str(r.get("creativeNo"));
            String convTypeRaw  = str(r.get("convType"));
            String targetDate   = str(r.getOrDefault("targetDate", date));
            if (campaignId.isEmpty() || campaignId.equals("0")) continue;
            if (adgroupId.isEmpty()  || adgroupId.equals("0"))  continue;
            if (adId.isEmpty()       || adId.equals("0"))        continue;
            if (convTypeRaw.isEmpty()) continue;

            String convTypeCode = normalizeConvTypeCode(convTypeRaw);
            String key = advkey + "|" + date + "|" + campaignId + "|" + adgroupId + "|" + adId + "|" + convTypeCode;

            bucket.computeIfAbsent(key, k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("daily_advid",     advkey);
                m.put("daily_dt",        targetDate);
                m.put("campaign_id",     campaignId);
                m.put("adgroup_id",      adgroupId);
                m.put("ad_id",           adId);
                m.put("conv_type_code",  convTypeCode);
                m.put("conv_type_name",  getConvTypeName(convTypeCode));
                m.put("conv_cnt",        0.0);
                m.put("conv_value",      0.0);
                return m;
            });

            Map<String, Object> entry = bucket.get(key);
            entry.put("conv_cnt",   toDouble(entry.get("conv_cnt"))   + toDouble(r.get("convCount")));
            entry.put("conv_value", toDouble(entry.get("conv_value")) + toDouble(r.get("convSales")));
        }

        if (bucket.isEmpty()) return 0;
        mongoService.bulkUpsertGfaAdConvType(new ArrayList<>(bucket.values()));
        return bucket.size();
    }

    // =====================================================================
    // HTTP (페이징 + 429/5xx 재시도)
    // =====================================================================

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchAllPages(String baseUrl, String token, String managerNo) {
        List<Map<String, Object>> all = new ArrayList<>();
        RestTemplate rt = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        headers.set("AccessManagerAccountNo", managerNo);
        HttpEntity<?> entity = new HttpEntity<>(headers);

        String url = baseUrl;

        do {
            Map<String, Object> body = null;

            for (int attempt = 0; attempt <= RETRY_MAX; attempt++) {
                try {
                    ResponseEntity<Map> res = rt.exchange(url, HttpMethod.GET, entity, Map.class);
                    body = res.getBody();
                    break;
                } catch (HttpStatusCodeException e) {
                    int status = e.getStatusCode().value();
                    if (status == 429 || (status >= 500 && status <= 504)) {
                        long sleepMs = Math.min(30000L,
                            (long) Math.pow(2, attempt) * 1000 + new Random().nextInt(1000));
                        log.warn("[GFA][CONV-TYPE] 재시도 http={} sleep={}ms attempt={}", status, sleepMs, attempt);
                        randomSleep((int) sleepMs, (int) sleepMs);
                    } else {
                        log.error("[GFA][CONV-TYPE] API 오류 http={} url={}", status, url);
                        return all;
                    }
                } catch (Exception e) {
                    log.error("[GFA][CONV-TYPE] API 예외 url={} error={}", url, e.getMessage());
                    return all;
                }
            }

            if (body == null) break;

            List<Map<String, Object>> rows = (List<Map<String, Object>>) body.get("rows");
            if (rows != null) all.addAll(rows);

            String next = body.get("next") != null ? String.valueOf(body.get("next")) : null;
            if (next == null || next.equals("null")) break;

            randomSleep(900, 1600);
            url = baseUrl + "&next=" + next;

        } while (true);

        return all;
    }

    // =====================================================================
    // 날짜 목록 생성
    // =====================================================================

    private List<String> buildAutoDates(String advkey) {
        Set<String> dates = new LinkedHashSet<>();
        LocalDate today = LocalDate.now();
        dates.add(today.minusDays(1).format(DATE_FMT));
        dates.add(today.minusDays(3).format(DATE_FMT));
        dates.add(today.minusDays(5).format(DATE_FMT));
        for (int i = 1; i <= 7; i++) {
            String d = today.minusDays(i).format(DATE_FMT);
            boolean cpMiss = !mongoService.hasGfaCampaignConvTypeData(advkey, d);
            boolean agMiss = !mongoService.hasGfaAdgroupConvTypeData(advkey, d);
            boolean adMiss = !mongoService.hasGfaAdConvTypeData(advkey, d);
            if (cpMiss || agMiss || adMiss) dates.add(d);
        }
        return new ArrayList<>(dates);
    }

    private List<String> buildDateRange(String from, String to) {
        List<String> dates = new ArrayList<>();
        LocalDate cur = LocalDate.parse(from, DATE_FMT);
        LocalDate end = LocalDate.parse(to, DATE_FMT);
        while (!cur.isAfter(end)) {
            dates.add(cur.format(DATE_FMT));
            cur = cur.plusDays(1);
        }
        return dates;
    }

    // =====================================================================
    // 전환유형 코드 정규화
    // =====================================================================

    private String normalizeConvTypeCode(String raw) {
        String code = raw.toUpperCase().trim();
        if (code.equals("PURCHASE") || code.equals("IAP"))                              return "purchase";
        if (code.equals("MEMBERSHIP"))                                                   return "sign_up";
        if (code.equals("CART") || code.equals("WISHLIST"))                             return "add_to_cart";
        if (code.equals("RESERVATION") || code.equals("SUBSCRIBE") || code.equals("SCHEDULE")) return "lead";
        return "other";
    }

    private String getConvTypeName(String code) {
        switch (code) {
            case "purchase":    return "구매완료";
            case "sign_up":     return "회원가입";
            case "add_to_cart": return "장바구니담기";
            case "lead":        return "신청완료";
            default:            return "기타";
        }
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private void randomSleep(int minMs, int maxMs) {
        try {
            Thread.sleep(minMs + new Random().nextInt(Math.max(1, maxMs - minMs)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String str(Object val) {
        if (val == null) return "";
        String s = val.toString().trim();
        return "null".equals(s) ? "" : s;
    }

    private double toDouble(Object val) {
        if (val == null) return 0.0;
        if (val instanceof Number) return ((Number) val).doubleValue();
        try { return Double.parseDouble(val.toString()); } catch (Exception e) { return 0.0; }
    }
}
