package com.h3.h3_java.batch.stat;

import com.h3.h3_java.batch.scheduler.NaverGfaTokenManager;
import com.h3.h3_java.media.naver.dto.NaverGfaAccountDto;
import com.h3.h3_java.raw.mongo.AccountMongoService;
import com.h3.h3_java.raw.mongo.AccountLogMongoService;
import com.h3.h3_java.raw.mongo.NaverGfaMasterMongoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class NaverGfaAdgroupDayCollectionJob {

    private final AccountMongoService accountMongo;
    private final NaverGfaMasterMongoService mongoService;
    private final NaverGfaTokenManager       tokenManager;
    private final AccountLogMongoService     accountLogMongo;

    private static final String GFA_DAILY_BASE = "https://openapi.naver.com/v1/ad-api/1.0";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public void collect() {
        List<NaverGfaAccountDto> accounts = accountMongo.findGfaAccountDtos();
        for (NaverGfaAccountDto acc : accounts) {
            collectAccount(acc, null, null);
        }
    }

    public boolean collectForUserId(String userId) {
        NaverGfaAccountDto acc = accountMongo.findGfaAccountDtos().stream()
            .filter(a -> userId.equals(a.getUserId()))
            .findFirst().orElse(null);
        if (acc == null) return false;
        collectAccount(acc, null, null);
        return true;
    }

    public void collectRange(String userId, String fromDate, String toDate) {
        NaverGfaAccountDto acc = accountMongo.findGfaAccountDtos().stream()
            .filter(a -> userId.equals(a.getUserId()))
            .findFirst().orElse(null);
        if (acc == null) return;
        collectAccount(acc, fromDate, toDate);
    }

    private record DateRange(String from, String to) {}

    private void collectAccount(NaverGfaAccountDto acc, String fromDate, String toDate) {
        String advkey = acc.getAccountGfa();
        String token = tokenManager.getAccessToken();
        String managerNo = tokenManager.getAccessManagerAccountNo();

        if (token == null || managerNo == null) {
            log.warn("[GFA][ADGROUP-DAY][SKIP] 토큰 없음 userId={}", acc.getUserId());
            return;
        }

        // gid → cid 매핑
        Map<String, String> adgroupMap = mongoService.selectGfaAdgroups(advkey);
        if (adgroupMap.isEmpty()) {
            log.warn("[GFA][ADGROUP-DAY] 광고그룹 없음 advkey={}", advkey);
            return;
        }

        if (fromDate != null && toDate != null) {
            for (DateRange chunk : buildChunkedRanges(fromDate, toDate)) {
                if (!collectChunk(advkey, token, managerNo, adgroupMap, chunk.from(), chunk.to())) break;
                try { Thread.sleep(1000 + new Random().nextInt(400)); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
            }
        } else {
            for (String date : buildAutoDates(advkey)) {
                if (!collectDay(advkey, token, managerNo, adgroupMap, date)) break;
                try { Thread.sleep(1000 + new Random().nextInt(400)); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
            }
        }
        accountLogMongo.updateField(advkey, "naverda", "adgroup");
    }

    @SuppressWarnings("unchecked")
    private boolean collectChunk(String advkey, String token, String managerNo,
                                 Map<String, String> adgroupMap, String fromDate, String toDate) {
        String baseUrl = GFA_DAILY_BASE + "/adAccounts/" + advkey
            + "/performance/past/adSets?startDate=" + fromDate + "&endDate=" + toDate + "&limit=1000";

        RestTemplate rt = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        headers.set("AccessManagerAccountNo", managerNo);
        HttpEntity<?> entity = new HttpEntity<>(headers);

        String url = baseUrl;
        int saved = 0;

        do {
            Map<String, Object> body;
            try {
                ResponseEntity<Map> res = rt.exchange(url, HttpMethod.GET, entity, Map.class);
                body = res.getBody();
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode().value() == 403) {
                    log.warn("[GFA][ADGROUP-DAY] 접근 권한 없음 advkey={} → 계정 스킵", advkey);
                    return false;
                }
                log.error("[GFA][ADGROUP-DAY] API 오류 advkey={} from={} to={} error={}", advkey, fromDate, toDate, e.getMessage());
                break;
            } catch (Exception e) {
                log.error("[GFA][ADGROUP-DAY] API 오류 advkey={} from={} to={} error={}", advkey, fromDate, toDate, e.getMessage());
                break;
            }

            if (body == null) break;

            List<Map<String, Object>> rows = (List<Map<String, Object>>) body.get("rows");
            if (rows != null) {
                for (Map<String, Object> r : rows) {
                    String adSetNo = String.valueOf(r.getOrDefault("adSetNo", ""));
                    if (!adgroupMap.containsKey(adSetNo)) continue;

                    String date = toDateStr(r);
                    if (date == null) {
                        log.warn("[GFA][ADGROUP-DAY] chunk 응답 date 필드 없음 advkey={} rowKeys={}", advkey, r.keySet());
                        continue;
                    }

                    long im  = toLong(r.get("impCount"));
                    long clk = toLong(r.get("clickCount"));
                    long cv  = toLong(r.get("convCount"));
                    double cst = toDouble(r.get("sales"));
                    double cr  = toDouble(r.get("convSales"));

                    if (im == 0 && clk == 0 && cst == 0 && cv == 0 && cr == 0) continue;

                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("daily_dt",    date);
                    row.put("daily_advid", advkey);
                    row.put("campaign_id", adgroupMap.get(adSetNo));
                    row.put("adgroup_id",  adSetNo);
                    row.put("daily_im",    im);
                    row.put("daily_clk",   clk);
                    row.put("daily_cst",   cst);
                    row.put("daily_cv",    cv);
                    row.put("daily_cr",    cr);

                    mongoService.upsertGfaAdgroupDaily(row);
                    saved++;
                }
            }

            String next = body.get("next") != null ? String.valueOf(body.get("next")) : null;
            if (next == null || next.equals("null")) break;
            url = baseUrl + "&next=" + next;

        } while (true);

        log.info("[GFA][ADGROUP-DAY] chunk advkey={} from={} to={} saved={}", advkey, fromDate, toDate, saved);
        return true;
    }

    @SuppressWarnings("unchecked")
    private boolean collectDay(String advkey, String token, String managerNo,
                               Map<String, String> adgroupMap, String date) {
        if (!LocalDate.parse(date, DATE_FMT).isBefore(LocalDate.now())) return true;

        String baseUrl = GFA_DAILY_BASE + "/adAccounts/" + advkey
            + "/performance/past/adSets?startDate=" + date + "&endDate=" + date + "&limit=1000";

        RestTemplate rt = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        headers.set("AccessManagerAccountNo", managerNo);
        HttpEntity<?> entity = new HttpEntity<>(headers);

        String url = baseUrl;
        int saved = 0;

        do {
            Map<String, Object> body;
            try {
                ResponseEntity<Map> res = rt.exchange(url, HttpMethod.GET, entity, Map.class);
                body = res.getBody();
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode().value() == 403) {
                    log.warn("[GFA][ADGROUP-DAY] 접근 권한 없음 advkey={} → 계정 스킵", advkey);
                    return false;
                }
                log.error("[GFA][ADGROUP-DAY] API 오류 advkey={} date={} error={}", advkey, date, e.getMessage());
                break;
            } catch (Exception e) {
                log.error("[GFA][ADGROUP-DAY] API 오류 advkey={} date={} error={}", advkey, date, e.getMessage());
                break;
            }

            if (body == null) break;

            List<Map<String, Object>> rows = (List<Map<String, Object>>) body.get("rows");
            if (rows != null) {
                for (Map<String, Object> r : rows) {
                    String adSetNo = String.valueOf(r.getOrDefault("adSetNo", ""));
                    if (!adgroupMap.containsKey(adSetNo)) continue;

                    long im  = toLong(r.get("impCount"));
                    long clk = toLong(r.get("clickCount"));
                    long cv  = toLong(r.get("convCount"));
                    double cst = toDouble(r.get("sales"));
                    double cr  = toDouble(r.get("convSales"));

                    if (im == 0 && clk == 0 && cst == 0 && cv == 0 && cr == 0) continue;

                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("daily_dt",    date);
                    row.put("daily_advid", advkey);
                    row.put("campaign_id", adgroupMap.get(adSetNo));
                    row.put("adgroup_id",  adSetNo);
                    row.put("daily_im",    im);
                    row.put("daily_clk",   clk);
                    row.put("daily_cst",   cst);
                    row.put("daily_cv",    cv);
                    row.put("daily_cr",    cr);

                    mongoService.upsertGfaAdgroupDaily(row);
                    saved++;
                }
            }

            String next = body.get("next") != null ? String.valueOf(body.get("next")) : null;
            if (next == null || next.equals("null")) break;
            url = baseUrl + "&next=" + next;

        } while (true);

        log.info("[GFA][ADGROUP-DAY] advkey={} date={} saved={}", advkey, date, saved);
        return true;
    }

    private List<String> buildAutoDates(String advkey) {
        Set<String> dates = new LinkedHashSet<>();
        LocalDate today = LocalDate.now();

        dates.add(today.minusDays(1).format(DATE_FMT));
        dates.add(today.minusDays(3).format(DATE_FMT));
        dates.add(today.minusDays(5).format(DATE_FMT));

        for (int i = 1; i <= 7; i++) {
            String d = today.minusDays(i).format(DATE_FMT);
            if (!mongoService.hasGfaAdgroupDailyData(advkey, d)) {
                dates.add(d);
            }
        }

        return new ArrayList<>(dates);
    }

    private List<DateRange> buildChunkedRanges(String from, String to) {
        List<DateRange> chunks = new ArrayList<>();
        LocalDate start = LocalDate.parse(from, DATE_FMT);
        LocalDate end   = LocalDate.parse(to,   DATE_FMT);
        while (!start.isAfter(end)) {
            LocalDate chunkEnd = start.plusDays(29);
            if (chunkEnd.isAfter(end)) chunkEnd = end;
            chunks.add(new DateRange(start.format(DATE_FMT), chunkEnd.format(DATE_FMT)));
            start = chunkEnd.plusDays(1);
        }
        return chunks;
    }

    private String toDateStr(Map<String, Object> row) {
        Object v = row.get("targetDate");
        if (v == null) v = row.get("date");
        if (v == null) return null;
        String s = String.valueOf(v);
        if (s.length() == 8 && !s.contains("-"))
            return s.substring(0, 4) + "-" + s.substring(4, 6) + "-" + s.substring(6, 8);
        return s;
    }

    private long toLong(Object val) {
        if (val == null) return 0L;
        if (val instanceof Number) return ((Number) val).longValue();
        try { return Long.parseLong(val.toString()); } catch (Exception e) { return 0L; }
    }

    private double toDouble(Object val) {
        if (val == null) return 0.0;
        if (val instanceof Number) return ((Number) val).doubleValue();
        try { return Double.parseDouble(val.toString()); } catch (Exception e) { return 0.0; }
    }
}
