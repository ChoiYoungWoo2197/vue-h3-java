package com.h3.h3_java.batch.stat;

import com.h3.h3_java.media.naver.NaverApiClient;
import com.h3.h3_java.media.naver.dto.NaverAccountDto;
import com.h3.h3_java.media.naver.mapper.NaverMasterReportMapper;
import com.h3.h3_java.raw.mongo.AccountLogMongoService;
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
public class NaverCampaignDayCollectionJob {

    private final NaverMasterReportMapper mapper;
    private final NaverStatMongoService   statMongoService;
    private final AccountLogMongoService  accountLogMongo;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public void collect() {
        List<NaverAccountDto> accounts = mapper.selectNaverAccounts();
        for (NaverAccountDto acc : accounts) {
            if ("admin".equals(acc.getUserId())) continue;
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
            log.warn("[NaverCampaignDay] 캠페인 없음 customerId={}", customerId);
            return;
        }

        List<String> dates = (fromDate != null && toDate != null)
            ? buildDateRange(fromDate, toDate)
            : buildAutoDates(customerId);

        for (String date : dates) {
            collectDay(client, customerId, campaigns, date);
        }
        accountLogMongo.updateField(customerId, "naver", "campaign");
    }

    private void collectDay(NaverApiClient client, String customerId, List<Document> campaigns, String date) {
        if (!LocalDate.parse(date, DATE_FMT).isBefore(LocalDate.now())) return;

        int saved = 0;
        for (Document campaign : campaigns) {
            String campaignId = campaign.getString("campaignid");
            if (campaignId == null) continue;

            Map<String, Object> res = getStats(client, campaignId, date);
            if (res == null || !res.containsKey("data")) continue;

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> data = (List<Map<String, Object>>) res.get("data");
            if (data == null || data.isEmpty()) continue;

            Map<String, Object> d = data.get(0);

            long impCnt   = toLong(d.get("impCnt"));
            long clkCnt   = toLong(d.get("clkCnt"));
            long salesAmt = toLong(d.get("salesAmt"));
            long ccnt     = toLong(d.get("ccnt"));
            long convAmt  = toLong(d.get("convAmt"));

            if (impCnt == 0 && clkCnt == 0 && salesAmt == 0 && ccnt == 0 && convAmt == 0) continue;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("daily_advid", customerId);
            row.put("daily_dt",    date);
            row.put("campaign_id", campaignId);
            row.put("daily_im",    impCnt);
            row.put("daily_clk",   clkCnt);
            row.put("daily_cst",   salesAmt);
            row.put("daily_cv",    ccnt);
            row.put("daily_cr",    convAmt);

            statMongoService.upsertCampaignDaily(row);
            saved++;
        }

        log.info("[NaverCampaignDay] customerId={} date={} saved={}", customerId, date, saved);
    }

    private Map<String, Object> getStats(NaverApiClient client, String campaignId, String date) {
        try {
            Map<String, String> params = new LinkedHashMap<>();
            params.put("ids", campaignId);
            params.put("fields", "[\"clkCnt\",\"impCnt\",\"salesAmt\",\"ccnt\",\"convAmt\"]");
            params.put("timeIncrement", "allDays");
            params.put("timeRange", String.format("{\"since\":\"%s\",\"until\":\"%s\"}", date, date));
            return client.get("/stats", params);
        } catch (Exception e) {
            log.error("[NaverCampaignDay] stats 조회 실패 campaignId={} date={} error={}", campaignId, date, e.getMessage());
            return null;
        }
    }

    private List<String> buildAutoDates(String customerId) {
        Set<String> dates = new LinkedHashSet<>();
        LocalDate today = LocalDate.now();

        // 기본 수집: D-1, D-3, D-5
        dates.add(today.minusDays(1).format(DATE_FMT));
        dates.add(today.minusDays(3).format(DATE_FMT));
        dates.add(today.minusDays(5).format(DATE_FMT));

        // 최근 7일 누락분 재수집
        for (int i = 1; i <= 7; i++) {
            String d = today.minusDays(i).format(DATE_FMT);
            if (!statMongoService.hasCampaignDailyData(customerId, d)) {
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
}