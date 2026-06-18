package com.h3.h3_java.batch.stat;

import com.h3.h3_java.batch.scheduler.NaverGfaTokenManager;
import com.h3.h3_java.media.naver.dto.NaverGfaAccountDto;
import com.h3.h3_java.media.naver.mapper.NaverGfaMapper;
import com.h3.h3_java.raw.mongo.AccountLogMongoService;
import com.h3.h3_java.raw.mongo.NaverGfaMasterMongoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class NaverGfaCampaignDayCollectionJob {

    private final NaverGfaMapper gfaMapper;
    private final NaverGfaMasterMongoService mongoService;
    private final NaverGfaTokenManager       tokenManager;
    private final AccountLogMongoService     accountLogMongo;

    private static final String GFA_DAILY_BASE = "https://openapi.naver.com/v1/ad-api/1.0";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

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

    private void collectAccount(NaverGfaAccountDto acc, String fromDate, String toDate) {
        String advkey = acc.getAccountGfa();
        String token = tokenManager.getAccessToken();
        String managerNo = tokenManager.getAccessManagerAccountNo();

        if (token == null || managerNo == null) {
            log.warn("[GFA][CAMPAIGN-DAY][SKIP] 토큰 없음 userId={}", acc.getUserId());
            return;
        }

        Set<String> campaignIds = mongoService.selectGfaCampaignIds(advkey);
        if (campaignIds.isEmpty()) {
            log.warn("[GFA][CAMPAIGN-DAY] 캠페인 없음 advkey={}", advkey);
            return;
        }

        List<String> dates = (fromDate != null && toDate != null)
            ? buildDateRange(fromDate, toDate)
            : buildAutoDates(advkey);

        for (String date : dates) {
            collectDay(advkey, token, managerNo, campaignIds, date);
            try {
                Thread.sleep(1000 + new Random().nextInt(400));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        accountLogMongo.updateField(advkey, "naverda", "campaign");
    }

    @SuppressWarnings("unchecked")
    private void collectDay(String advkey, String token, String managerNo,
                            Set<String> campaignIds, String date) {
        if (!LocalDate.parse(date, DATE_FMT).isBefore(LocalDate.now())) return;

        String baseUrl = GFA_DAILY_BASE + "/adAccounts/" + advkey
            + "/performance/past/campaigns?startDate=" + date + "&endDate=" + date + "&limit=1000";

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
            } catch (Exception e) {
                log.error("[GFA][CAMPAIGN-DAY] API 오류 advkey={} date={} error={}", advkey, date, e.getMessage());
                break;
            }

            if (body == null) break;

            List<Map<String, Object>> rows = (List<Map<String, Object>>) body.get("rows");
            if (rows != null) {
                for (Map<String, Object> r : rows) {
                    String campaignNo = String.valueOf(r.getOrDefault("campaignNo", ""));
                    if (!campaignIds.contains(campaignNo)) continue;

                    long im  = toLong(r.get("impCount"));
                    long clk = toLong(r.get("clickCount"));
                    long cv  = toLong(r.get("convCount"));
                    double cst = toDouble(r.get("sales"));
                    double cr  = toDouble(r.get("convSales"));

                    if (im == 0 && clk == 0 && cst == 0 && cv == 0 && cr == 0) continue;

                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("daily_dt",    date);
                    row.put("daily_advid", advkey);
                    row.put("campaign_id", campaignNo);
                    row.put("daily_im",    im);
                    row.put("daily_clk",   clk);
                    row.put("daily_cst",   cst);
                    row.put("daily_cv",    cv);
                    row.put("daily_cr",    cr);

                    mongoService.upsertGfaCampaignDaily(row);
                    saved++;
                }
            }

            String next = body.get("next") != null ? String.valueOf(body.get("next")) : null;
            if (next == null || next.equals("null")) break;
            url = baseUrl + "&next=" + next;

        } while (true);

        log.info("[GFA][CAMPAIGN-DAY] advkey={} date={} saved={}", advkey, date, saved);
    }

    private List<String> buildAutoDates(String advkey) {
        Set<String> dates = new LinkedHashSet<>();
        LocalDate today = LocalDate.now();

        dates.add(today.minusDays(1).format(DATE_FMT));
        dates.add(today.minusDays(3).format(DATE_FMT));
        dates.add(today.minusDays(5).format(DATE_FMT));

        for (int i = 1; i <= 7; i++) {
            String d = today.minusDays(i).format(DATE_FMT);
            if (!mongoService.hasGfaCampaignDailyData(advkey, d)) {
                dates.add(d);
            }
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
