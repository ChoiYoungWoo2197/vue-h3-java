package com.h3.h3_java.batch.stat;

import com.h3.h3_java.media.naver.NaverApiClient;
import com.h3.h3_java.media.naver.dto.NaverAccountDto;
import com.h3.h3_java.media.naver.mapper.NaverSaBudgetAlarmMapper;
import com.h3.h3_java.raw.mongo.AccountMongoService;
import com.h3.h3_java.raw.mongo.NaverStatMongoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class NaverSaBudgetAlarmJob {

    private final AccountMongoService    accountMongo;
    private final NaverSaBudgetAlarmMapper alarmMapper;
    private final NaverStatMongoService  statMongo;

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final List<String> KPIS = List.of("im", "clk", "cv", "cr");
    private static final Map<String, String> KPI_NAMES = Map.of(
        "im",  "총노출수",
        "clk", "총클릭수",
        "cv",  "총전환수",
        "cr",  "총전환매출"
    );
    private static final Map<String, String> KPI_COLUMNS = Map.of(
        "im",  "daily_im",
        "clk", "daily_clk",
        "cv",  "daily_cv",
        "cr",  "daily_cr"
    );

    public void collect() {
        List<NaverAccountDto> accounts = accountMongo.findNaverAccountDtos();
        for (NaverAccountDto acc : accounts) {
            if ("admin".equals(acc.getUserId()) || "dydrp123".equals(acc.getUserId())) continue;
            collectAccount(acc);
        }
    }

    public boolean collectForUserId(String userId) {
        NaverAccountDto acc = accountMongo.findNaverAccountDtos().stream()
            .filter(a -> userId.equals(a.getUserId()))
            .findFirst().orElse(null);
        if (acc == null) return false;
        collectAccount(acc);
        return true;
    }

    private void collectAccount(NaverAccountDto acc) {
        String customerId = acc.getAccountNaverCustomer();
        NaverApiClient client = new NaverApiClient(
            acc.getAccountNaverAccess(),
            acc.getAccountNaverSecret(),
            customerId
        );

        log.info("[SA][BUDGET-ALARM][START] userId={} customerId={}", acc.getUserId(), customerId);

        // 1. 비즈머니 잠금 알람
        bizmoneyLockAlarm(acc.getUserId(), customerId, client);

        // 2. 계정/캠페인 KPI 알람 (알람 설정이 있는 경우만)
        Map<String, Object> alarmSetting = alarmMapper.selectAlarmSetting(acc.getUserId());
        if (alarmSetting == null) {
            log.debug("[SA][BUDGET-ALARM][SKIP] 알람설정 없음 userId={}", acc.getUserId());
            return;
        }

        int type = toInt(alarmSetting.get("type"));
        if (type == 1) {
            accountBudgetAlarm(acc.getUserId(), customerId, alarmSetting);
        } else {
            campaignBudgetAlarm(acc.getUserId(), customerId, alarmSetting, client);
        }
    }

    // ─── 비즈머니 잠금 알람 ────────────────────────────────────────────────────

    private void bizmoneyLockAlarm(String userId, String customerId, NaverApiClient client) {
        Map<String, Object> res = client.get("/billing/bizmoney");
        if (res == null || !res.containsKey("budgetLock")) return;

        Boolean budgetLock = (Boolean) res.get("budgetLock");
        if (!Boolean.TRUE.equals(budgetLock)) return;

        int cnt = alarmMapper.countRecentBizmoneyAlarm(userId, customerId);
        if (cnt > 0) return;

        String dateTime = LocalDateTime.now().format(DT_FMT);
        String content  = customerId + "계정 비즈머니 잔액 부족으로 계정잠김 상태가 되어 모든 광고 노출이 중지됐습니다.";

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("user_id",       userId);
        row.put("daily_advid",   customerId);
        row.put("daily_dt",      dateTime);
        row.put("media",         "naver");
        row.put("target_id",     null);
        row.put("target_name",   null);
        row.put("level",         "bizmoney");
        row.put("type",          "bizmoney_locked_by_budget");
        row.put("content",       content);
        row.put("daily_regdate", dateTime);

        alarmMapper.insertBudgetAlarm(row);
        log.info("[SA][BUDGET-ALARM] bizmoney 잠금 알람 userId={}", userId);
    }

    // ─── 계정 수준 KPI 인상률 알람 ───────────────────────────────────────────

    private void accountBudgetAlarm(String userId, String customerId, Map<String, Object> alarmSetting) {
        for (String kpi : KPIS) {
            String periodStr = getStr(alarmSetting.get(kpi));
            if (periodStr.length() < 2) continue;

            int cnt = alarmMapper.countRecentAlarm(userId, customerId, "account", null,
                "account_rate_updown_by_" + kpi);
            if (cnt > 0) continue;

            int day;
            try {
                day = Integer.parseInt(periodStr.substring(1)) - 1;
            } catch (NumberFormatException e) {
                continue;
            }

            LocalDate toDate    = LocalDate.now().minusDays(1);
            LocalDate fromDate  = toDate.minusDays(day);
            LocalDate cToDate   = fromDate.minusDays(1);
            LocalDate cFromDate = cToDate.minusDays(day);

            String col = KPI_COLUMNS.get(kpi);
            double a = statMongo.sumSaCampaignDaily(customerId, fromDate.toString(), toDate.toString(), col, null);
            double b = statMongo.sumSaCampaignDaily(customerId, cFromDate.toString(), cToDate.toString(), col, null);

            double per = calculateRate(b, a);
            if (per == 0) continue;

            String perStr   = String.format("%.2f", per);
            String content  = customerId + "계정의 " + KPI_NAMES.get(kpi) + " 인상률은 " + perStr + "% 입니다.";
            String dateTime = LocalDateTime.now().format(DT_FMT);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("user_id",       userId);
            row.put("daily_advid",   customerId);
            row.put("daily_dt",      dateTime);
            row.put("media",         "naver");
            row.put("target_id",     null);
            row.put("target_name",   null);
            row.put("level",         "account");
            row.put("type",          "account_rate_updown_by_" + kpi);
            row.put("content",       content);
            row.put("daily_regdate", dateTime);

            alarmMapper.insertBudgetAlarm(row);
            log.info("[SA][BUDGET-ALARM] account alarm userId={} kpi={} rate={}", userId, kpi, perStr);
        }
    }

    // ─── 캠페인 수준 알람 ─────────────────────────────────────────────────────

    private void campaignBudgetAlarm(String userId, String customerId,
                                     Map<String, Object> alarmSetting, NaverApiClient client) {
        List<Document> campaigns = statMongo.selectCampaignsByCustomer(customerId);
        if (campaigns.isEmpty()) {
            log.warn("[SA][BUDGET-ALARM] 캠페인 없음 customerId={}", customerId);
            return;
        }

        for (Document campaign : campaigns) {
            String campaignId   = campaign.getString("campaignid");
            String campaignName = campaign.getString("campaignname");
            if (campaignId == null) continue;

            // 캠페인 상세 조회 (하루예산 초과 체크)
            Map<String, Object> detail = client.get("/ncc/campaigns/" + campaignId);
            if (detail != null && Boolean.TRUE.equals(detail.get("useDailyBudget"))
                    && "CAMPAIGN_LIMITED_BY_BUDGET".equals(detail.get("statusReason"))) {

                int cnt = alarmMapper.countRecentAlarm(
                    userId, customerId, "campaign", campaignId, "campaign_limited_by_budget");
                if (cnt == 0) {
                    String dateTime = LocalDateTime.now().format(DT_FMT);
                    String content  = campaignName + " 캠페인 하루예산이 초과 혹은 초과 예상되어 캠페인이 중지되었습니다.";
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("user_id",       userId);
                    row.put("daily_advid",   customerId);
                    row.put("daily_dt",      dateTime);
                    row.put("media",         "naver");
                    row.put("target_id",     campaignId);
                    row.put("target_name",   campaignName);
                    row.put("level",         "campaign");
                    row.put("type",          "campaign_limited_by_budget");
                    row.put("content",       content);
                    row.put("daily_regdate", dateTime);
                    alarmMapper.insertBudgetAlarm(row);
                    log.info("[SA][BUDGET-ALARM] 캠페인 하루예산 초과 userId={} campaignId={}", userId, campaignId);
                }
            }

            // onoff=1 이면 꺼진 캠페인 → KPI 알람 스킵
            if (campaign.getInteger("onoff", 1) != 0) continue;

            // 캠페인 KPI 인상률 알람
            for (String kpi : KPIS) {
                String periodStr = getStr(alarmSetting.get(kpi));
                if (periodStr.length() < 2) continue;

                int cnt = alarmMapper.countRecentAlarm(
                    userId, customerId, "campaign", campaignId, "campaign_rate_updown_by_" + kpi);
                if (cnt > 0) continue;

                int day;
                try {
                    day = Integer.parseInt(periodStr.substring(1)) - 1;
                } catch (NumberFormatException e) {
                    continue;
                }

                LocalDate toDate    = LocalDate.now().minusDays(1);
                LocalDate fromDate  = toDate.minusDays(day);
                LocalDate cToDate   = fromDate.minusDays(1);
                LocalDate cFromDate = cToDate.minusDays(day);

                String col = KPI_COLUMNS.get(kpi);
                double a = statMongo.sumSaCampaignDaily(customerId, fromDate.toString(), toDate.toString(), col, campaignId);
                double b = statMongo.sumSaCampaignDaily(customerId, cFromDate.toString(), cToDate.toString(), col, campaignId);

                double per = calculateRate(b, a);
                if (per == 0) continue;

                String perStr   = String.format("%.2f", per);
                String content  = campaignName + "캠페인의 " + KPI_NAMES.get(kpi) + " 인상률은 " + perStr + "% 입니다.";
                String dateTime = LocalDateTime.now().format(DT_FMT);

                Map<String, Object> row = new LinkedHashMap<>();
                row.put("user_id",       userId);
                row.put("daily_advid",   customerId);
                row.put("daily_dt",      dateTime);
                row.put("media",         "naver");
                row.put("target_id",     campaignId);
                row.put("target_name",   campaignName);
                row.put("level",         "campaign");
                row.put("type",          "campaign_rate_updown_by_" + kpi);
                row.put("content",       content);
                row.put("daily_regdate", dateTime);

                alarmMapper.insertBudgetAlarm(row);
                log.info("[SA][BUDGET-ALARM] campaign alarm userId={} campaignId={} kpi={} rate={}",
                    userId, campaignId, kpi, perStr);
            }
        }
    }

    private double calculateRate(double b, double a) {
        if (b == 0) return 0;
        return (a - b) / b * 100;
    }

    private int toInt(Object val) {
        if (val == null) return 0;
        try { return ((Number) val).intValue(); } catch (Exception e) { return 0; }
    }

    private String getStr(Object val) {
        if (val == null) return "";
        String s = val.toString();
        return "null".equals(s) ? "" : s;
    }
}
