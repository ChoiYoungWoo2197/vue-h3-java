package com.h3.h3_java.batch.stat;

import com.h3.h3_java.media.naver.NaverApiClient;
import com.h3.h3_java.media.naver.dto.NaverAccountDto;
import com.h3.h3_java.media.naver.dto.NaverShoppingAdDto;
import com.h3.h3_java.media.naver.mapper.NaverMasterReportMapper;
import com.h3.h3_java.raw.mongo.NaverStatMongoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class NaverShoppingAdDayCollectionJob {

    private final NaverMasterReportMapper mapper;
    private final NaverStatMongoService statMongoService;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final int BATCH_SIZE   = 200;
    private static final int MAX_ROWS_DAY = 10000;
    private static final int MAX_RETRY    = 2;

    public void collect() {
        List<NaverAccountDto> accounts = mapper.selectNaverAccounts();
        Set<String> seen = new HashSet<>();
        for (NaverAccountDto acc : accounts) {
            if ("admin".equals(acc.getUserId())) continue;
            String cid = acc.getAccountNaverCustomer();
            if (cid == null || cid.isBlank() || !seen.add(cid)) continue;
            collectAccount(acc, null, null);
        }
    }

    public boolean collectForUserId(String userId) {
        NaverAccountDto acc = mapper.selectNaverAccounts().stream()
            .filter(a -> userId.equals(a.getUserId()))
            .findFirst().orElse(null);
        if (acc == null) return false;
        collectAccount(acc, null, null);
        return true;
    }

    public void collectRange(String userId, String fromDate, String toDate) {
        NaverAccountDto acc = mapper.selectNaverAccounts().stream()
            .filter(a -> userId.equals(a.getUserId()))
            .findFirst().orElse(null);
        if (acc == null) return;
        collectAccount(acc, fromDate, toDate);
    }

    private void collectAccount(NaverAccountDto acc, String fromDate, String toDate) {
        String customerId = acc.getAccountNaverCustomer();
        NaverApiClient client = new NaverApiClient(
            acc.getAccountNaverAccess(),
            acc.getAccountNaverSecret(),
            customerId
        );

        List<NaverShoppingAdDto> ads = statMongoService.selectShoppingAdsByCustomer(customerId);
        if (ads.isEmpty()) {
            log.warn("[NaverShoppingAdDay] 쇼핑소재 없음 customerId={}", customerId);
            return;
        }

        List<String> dates = (fromDate != null && toDate != null)
            ? buildDateRange(fromDate, toDate)
            : buildAutoDates();

        List<String> failedDates = new ArrayList<>();

        for (String date : dates) {
            try {
                int saved = collectDay(client, customerId, ads, date);
                log.info("[NaverShoppingAdDay] customerId={} date={} saved={}", customerId, date, saved);
            } catch (Exception e) {
                log.error("[NaverShoppingAdDay] 실패 customerId={} date={} error={}", customerId, date, e.getMessage());
                failedDates.add(date);
            }
        }

        for (int retry = 1; retry <= MAX_RETRY && !failedDates.isEmpty(); retry++) {
            log.info("[NaverShoppingAdDay] RETRY {} customerId={} dates={}", retry, customerId, failedDates);
            List<String> stillFailed = new ArrayList<>();
            for (String date : failedDates) {
                try {
                    int saved = collectDay(client, customerId, ads, date);
                    log.info("[NaverShoppingAdDay] RETRY{} customerId={} date={} saved={}", retry, customerId, date, saved);
                } catch (Exception e) {
                    log.error("[NaverShoppingAdDay] RETRY{} 실패 customerId={} date={} error={}", retry, customerId, date, e.getMessage());
                    stillFailed.add(date);
                }
            }
            failedDates = stillFailed;
        }
    }

    private int collectDay(NaverApiClient client, String customerId, List<NaverShoppingAdDto> ads, String date) {
        if (!LocalDate.parse(date, DATE_FMT).isBefore(LocalDate.now())) return 0;

        Map<String, long[]> resultMap = new LinkedHashMap<>();
        for (NaverShoppingAdDto ad : ads) {
            if (ad.getAdId() == null || ad.getAdId().isEmpty()) continue;
            resultMap.put(ad.getAdId(), new long[]{0L, 0L, 0L, 0L, 0L});
        }

        List<String> batch = new ArrayList<>();
        for (NaverShoppingAdDto ad : ads) {
            if (ad.getAdId() == null || ad.getAdId().isEmpty()) continue;
            batch.add(ad.getAdId());
            if (batch.size() == BATCH_SIZE) {
                fetchStats(client, batch, resultMap, date);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) fetchStats(client, batch, resultMap, date);

        int saved = 0;
        for (NaverShoppingAdDto ad : ads) {
            if (saved >= MAX_ROWS_DAY) break;

            String adId = ad.getAdId();
            if (adId == null || adId.isEmpty()) continue;

            long[] vals = resultMap.getOrDefault(adId, new long[]{0L, 0L, 0L, 0L, 0L});
            if (vals[0] == 0 && vals[1] == 0 && vals[2] == 0 && vals[3] == 0 && vals[4] == 0) continue;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("daily_advid", customerId);
            row.put("daily_dt",    date);
            row.put("campaign_id", ad.getCampaignId() != null ? ad.getCampaignId() : "");
            row.put("adgroup_id",  ad.getAdgroupId()  != null ? ad.getAdgroupId()  : "");
            row.put("ad_id",       adId);
            row.put("ad_pid",      ad.getAdPid()      != null ? ad.getAdPid()      : "");
            row.put("daily_im",    vals[0]);
            row.put("daily_clk",   vals[1]);
            row.put("daily_cst",   vals[2]);
            row.put("daily_cv",    vals[3]);
            row.put("daily_cr",    vals[4]);

            statMongoService.upsertShoppingAdDaily(row);
            saved++;
        }
        return saved;
    }

    @SuppressWarnings("unchecked")
    private void fetchStats(NaverApiClient client, List<String> ids, Map<String, long[]> resultMap, String date) {

        Map<String, String> params = new LinkedHashMap<>();
        params.put("ids",       String.join(",", ids));
        params.put("fields",    "[\"clkCnt\",\"impCnt\",\"salesAmt\",\"ccnt\",\"convAmt\"]");
        params.put("timeRange", String.format("{\"since\":\"%s\",\"until\":\"%s\"}", date, date));

        Map<String, Object> res = client.get("/stats", params);
        if (res == null || !res.containsKey("data")) return;

        List<Map<String, Object>> data = (List<Map<String, Object>>) res.get("data");
        if (data == null) return;

        for (Map<String, Object> v : data) {
            String id = String.valueOf(v.getOrDefault("id", ""));
            if (!resultMap.containsKey(id)) continue;
            long[] vals = resultMap.get(id);
            vals[0] = toLong(v.get("impCnt"));
            vals[1] = toLong(v.get("clkCnt"));
            vals[2] = toLong(v.get("salesAmt"));
            vals[3] = toLong(v.get("ccnt"));
            vals[4] = toLong(v.get("convAmt"));
        }
    }

    private List<String> buildAutoDates() {
        Set<String> dates = new LinkedHashSet<>();
        LocalDate today = LocalDate.now();
        dates.add(today.minusDays(1).format(DATE_FMT));
        dates.add(today.minusDays(3).format(DATE_FMT));
        dates.add(today.minusDays(5).format(DATE_FMT));
        return new ArrayList<>(dates);
    }

    private List<String> buildDateRange(String from, String to) {
        List<String> dates = new ArrayList<>();
        LocalDate cur = LocalDate.parse(from, DATE_FMT);
        LocalDate end = LocalDate.parse(to,   DATE_FMT);
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
}
