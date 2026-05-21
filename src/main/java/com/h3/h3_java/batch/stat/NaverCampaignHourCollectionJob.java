package com.h3.h3_java.batch.stat;

import com.h3.h3_java.media.naver.NaverApiClient;
import com.h3.h3_java.media.naver.dto.NaverAccountDto;
import com.h3.h3_java.media.naver.mapper.NaverMasterReportMapper;
import com.h3.h3_java.raw.mongo.NaverStatMongoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class NaverCampaignHourCollectionJob {

    private final NaverMasterReportMapper mapper;
    private final NaverStatMongoService statMongoService;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // "00" -> "00시~01시", "23" -> "23시~00시"
    private static final Map<String, String> HOUR_NAMES = new LinkedHashMap<>();
    private static final Map<String, String> NAME_TO_HH = new LinkedHashMap<>();
    static {
        for (int h = 0; h <= 23; h++) {
            String hh   = String.format("%02d", h);
            String next = String.format("%02d", (h + 1) % 24);
            String name = hh + "시~" + next + "시";
            HOUR_NAMES.put(hh, name);
            NAME_TO_HH.put(name, hh);
        }
    }

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

        List<Document> campaigns = statMongoService.selectCampaignsByCustomer(customerId);
        if (campaigns.isEmpty()) {
            log.warn("[NaverCampaignHour] 캠페인 없음 customerId={}", customerId);
            return;
        }

        List<String> dates = (fromDate != null && toDate != null)
            ? buildDateRange(fromDate, toDate)
            : buildAutoDates(customerId);

        for (String date : dates) {
            int saved = collectDay(client, customerId, campaigns, date);
            log.info("[NaverCampaignHour] customerId={} date={} saved={}", customerId, date, saved);
        }
    }

    @SuppressWarnings("unchecked")
    private int collectDay(NaverApiClient client, String customerId, List<Document> campaigns, String date) {
        if (!LocalDate.parse(date, DATE_FMT).isBefore(LocalDate.now())) return 0;

        // 집계 초기화 - 일 합계
        Map<String, Long> data = new LinkedHashMap<>();
        data.put("clk", 0L); data.put("im", 0L); data.put("cst", 0L);
        data.put("cv",  0L); data.put("cr", 0L);

        // 시간대별 초기화
        for (String hh : HOUR_NAMES.keySet()) {
            data.put("hour_" + hh + "_im",  0L);
            data.put("hour_" + hh + "_clk", 0L);
            data.put("hour_" + hh + "_cst", 0L);
            data.put("hour_" + hh + "_cv",  0L);
            data.put("hour_" + hh + "_cr",  0L);
        }

        for (Document campaign : campaigns) {
            String campaignId = campaign.getString("campaignid");
            if (campaignId == null) continue;

            Map<String, String> params = new LinkedHashMap<>();
            params.put("ids",           campaignId);
            params.put("fields",        "[\"clkCnt\",\"impCnt\",\"salesAmt\",\"ccnt\",\"convAmt\"]");
            params.put("timeIncrement", "allDays");
            params.put("timeRange",     String.format("{\"since\":\"%s\",\"until\":\"%s\"}", date, date));
            params.put("breakdown",     "hh24");

            Map<String, Object> res = client.get("/stats", params);
            if (res == null || !res.containsKey("data")) continue;

            List<Map<String, Object>> dataList = (List<Map<String, Object>>) res.get("data");
            if (dataList == null || dataList.isEmpty()) continue;

            Map<String, Object> d = dataList.get(0);

            // 캠페인 일 합계 누적
            data.merge("im",  toLong(d.get("impCnt")),   Long::sum);
            data.merge("clk", toLong(d.get("clkCnt")),   Long::sum);
            data.merge("cst", toLong(d.get("salesAmt")), Long::sum);
            data.merge("cv",  toLong(d.get("ccnt")),     Long::sum);
            data.merge("cr",  toLong(d.get("convAmt")),  Long::sum);

            // 시간대 breakdown 누적
            List<Map<String, Object>> breakdowns = (List<Map<String, Object>>) d.get("breakdowns");
            if (breakdowns == null) continue;

            for (Map<String, Object> b : breakdowns) {
                String bName = String.valueOf(b.getOrDefault("name", ""));
                String hh    = NAME_TO_HH.get(bName);
                if (hh == null) continue;

                data.merge("hour_" + hh + "_im",  toLong(b.get("impCnt")),   Long::sum);
                data.merge("hour_" + hh + "_clk", toLong(b.get("clkCnt")),   Long::sum);
                data.merge("hour_" + hh + "_cst", toLong(b.get("salesAmt")), Long::sum);
                data.merge("hour_" + hh + "_cv",  toLong(b.get("ccnt")),     Long::sum);
                data.merge("hour_" + hh + "_cr",  toLong(b.get("convAmt")),  Long::sum);
            }
        }

        // 전부 0이면 저장 안함
        if (data.get("clk") == 0 && data.get("im") == 0 && data.get("cst") == 0
                && data.get("cv") == 0 && data.get("cr") == 0) return 0;

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("adv_id",  customerId);
        row.put("hour_dt", date);
        data.forEach(row::put);

        statMongoService.upsertCampaignHour(row);
        return 1;
    }

    private List<String> buildAutoDates(String customerId) {
        Set<String> dates = new LinkedHashSet<>();
        LocalDate today = LocalDate.now();
        dates.add(today.minusDays(1).format(DATE_FMT));
        dates.add(today.minusDays(3).format(DATE_FMT));
        dates.add(today.minusDays(5).format(DATE_FMT));
        for (int i = 1; i <= 7; i++) {
            String d = today.minusDays(i).format(DATE_FMT);
            if (!statMongoService.hasCampaignHourData(customerId, d)) dates.add(d);
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
}
