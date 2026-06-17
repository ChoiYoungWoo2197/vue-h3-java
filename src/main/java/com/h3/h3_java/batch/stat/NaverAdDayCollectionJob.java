package com.h3.h3_java.batch.stat;

import com.h3.h3_java.media.naver.NaverApiClient;
import com.h3.h3_java.media.naver.dto.NaverAccountDto;
import com.h3.h3_java.media.naver.dto.NaverAdDto;
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
public class NaverAdDayCollectionJob {

    private final NaverMasterReportMapper mapper;
    private final NaverStatMongoService statMongoService;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final int BATCH_SIZE   = 200;
    private static final int MAX_ROWS_DAY = 5000;

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

        List<NaverAdDto> ads = statMongoService.selectAdsByCustomer(customerId);
        if (ads.isEmpty()) {
            log.warn("[NaverAdDay] 소재 없음 customerId={}", customerId);
            return;
        }

        List<String> dates = (fromDate != null && toDate != null)
            ? buildDateRange(fromDate, toDate)
            : buildAutoDates(customerId);

        for (String date : dates) {
            int saved = collectDay(client, customerId, ads, date);
            log.info("[NaverAdDay] customerId={} date={} saved={}", customerId, date, saved);
        }
    }

    private int collectDay(NaverApiClient client, String customerId, List<NaverAdDto> ads, String date) {
        if (!LocalDate.parse(date, DATE_FMT).isBefore(LocalDate.now())) return 0;

        Map<String, long[]> resultMap = new LinkedHashMap<>();
        for (NaverAdDto ad : ads) {
            if (ad.getAdId() == null || ad.getAdId().isEmpty()) continue;
            resultMap.put(ad.getAdId(), new long[]{0L, 0L, 0L, 0L, 0L});
        }

        List<String> batch = new ArrayList<>();
        for (NaverAdDto ad : ads) {
            if (ad.getAdId() == null || ad.getAdId().isEmpty()) continue;
            batch.add(ad.getAdId());
            if (batch.size() == BATCH_SIZE) {
                fetchStats(client, batch, resultMap, date);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) fetchStats(client, batch, resultMap, date);

        int saved = 0;
        for (NaverAdDto ad : ads) {
            if (saved >= MAX_ROWS_DAY) break;

            String adId       = ad.getAdId();
            String campaignId = ad.getCampaignId();
            String adgroupId  = ad.getAdgroupId();

            if (adId == null || adId.isEmpty()) continue;
            if (campaignId == null || campaignId.isEmpty()) continue;
            if (adgroupId  == null || adgroupId.isEmpty()) continue;

            long[] vals = resultMap.getOrDefault(adId, new long[]{0L, 0L, 0L, 0L, 0L});
            if (vals[0] == 0 && vals[1] == 0 && vals[2] == 0 && vals[3] == 0 && vals[4] == 0) continue;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("daily_advid", customerId);
            row.put("daily_dt",    date);
            row.put("campaign_id", campaignId);
            row.put("adgroup_id",  adgroupId);
            row.put("ad_id",       adId);
            row.put("daily_im",    vals[0]);
            row.put("daily_clk",   vals[1]);
            row.put("daily_cst",   vals[2]);
            row.put("daily_cv",    vals[3]);
            row.put("daily_cr",    vals[4]);

            statMongoService.upsertAdDaily(row);
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

        Map<String, Object> res;
        try {
            res = client.get("/stats", params);
        } catch (Exception e) {
            log.error("[NaverAdDay] stats 조회 실패 date={} error={}", date, e.getMessage());
            return;
        }
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

    private List<String> buildAutoDates(String customerId) {
        Set<String> dates = new LinkedHashSet<>();
        LocalDate today = LocalDate.now();
        dates.add(today.minusDays(1).format(DATE_FMT));
        dates.add(today.minusDays(3).format(DATE_FMT));
        dates.add(today.minusDays(5).format(DATE_FMT));
        for (int i = 1; i <= 7; i++) {
            String d = today.minusDays(i).format(DATE_FMT);
            if (!statMongoService.hasAdDailyData(customerId, d)) dates.add(d);
        }
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
