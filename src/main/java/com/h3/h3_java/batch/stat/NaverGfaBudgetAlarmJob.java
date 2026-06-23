package com.h3.h3_java.batch.stat;

import com.h3.h3_java.batch.scheduler.NaverGfaTokenManager;
import com.h3.h3_java.media.naver.dto.NaverGfaAccountDto;
import com.h3.h3_java.media.naver.mapper.NaverGfaBudgetAlarmMapper;
import com.h3.h3_java.raw.mongo.AccountMongoService;
import com.h3.h3_java.raw.mongo.NaverGfaMasterMongoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class NaverGfaBudgetAlarmJob {

    private final AccountMongoService accountMongo;
    private final NaverGfaBudgetAlarmMapper alarmMapper;
    private final NaverGfaMasterMongoService mongoService;
    private final NaverGfaTokenManager tokenManager;

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final List<String> KPIS = List.of("im", "clk", "cv", "cr");
    private static final Map<String, String> KPI_NAMES = Map.of(
        "im",  "총 노출수",
        "clk", "총 클릭수",
        "cv",  "총 전환수",
        "cr",  "총 전환매출"
    );
    private static final Map<String, String> KPI_COLUMNS = Map.of(
        "im",  "daily_im",
        "clk", "daily_clk",
        "cv",  "daily_cv",
        "cr",  "daily_cr"
    );

    public void collect() {
        List<NaverGfaAccountDto> accounts = accountMongo.findGfaAccountDtos();
        for (NaverGfaAccountDto acc : accounts) {
            collectAccount(acc);
        }
    }

    public boolean collectForUserId(String userId) {
        NaverGfaAccountDto acc = accountMongo.findGfaAccountDtos().stream()
            .filter(a -> userId.equals(a.getUserId()))
            .findFirst().orElse(null);
        if (acc == null) return false;
        collectAccount(acc);
        return true;
    }

    private void collectAccount(NaverGfaAccountDto acc) {
        String token = tokenManager.getAccessToken();
        if (token == null) {
            log.warn("[GFA][BUDGET-ALARM][SKIP] 토큰 없음 userId={}", acc.getUserId());
            return;
        }

        Map<String, Object> alarmSetting = alarmMapper.selectAlarmSetting(acc.getUserId());
        if (alarmSetting == null) {
            log.debug("[GFA][BUDGET-ALARM][SKIP] 알람설정 없음 userId={}", acc.getUserId());
            return;
        }

        String advkey = acc.getAccountGfa();
        int type = toInt(alarmSetting.get("type"));

        log.info("[GFA][BUDGET-ALARM][START] userId={} advkey={} type={}", acc.getUserId(), advkey, type);

        if (type == 1) {
            accountBudgetAlarm(acc.getUserId(), advkey, alarmSetting);
        } else {
            campaignBudgetAlarm(acc.getUserId(), advkey, alarmSetting);
        }
    }

    private void accountBudgetAlarm(String userId, String advkey, Map<String, Object> alarmSetting) {
        for (String kpi : KPIS) {
            String periodStr = getStr(alarmSetting.get(kpi));
            if (periodStr.length() < 2) continue;

            int existingCount = alarmMapper.countRecentAlarm(
                userId, advkey, "account", null, "account_rate_updown_by_" + kpi);
            if (existingCount > 0) continue;

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
            double a = mongoService.sumGfaCampaignDaily(advkey, fromDate.toString(), toDate.toString(), col, null);
            double b = mongoService.sumGfaCampaignDaily(advkey, cFromDate.toString(), cToDate.toString(), col, null);

            double per = calculateIncreaseRate(b, a);
            if (per == 0) continue;

            String perStr  = String.format("%.2f", per);
            String content = advkey + "계정의 " + KPI_NAMES.get(kpi) + " 인상률은 " + perStr + "% 입니다.";
            String dateTime = LocalDateTime.now().format(DT_FMT);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("user_id",      userId);
            row.put("daily_advid",  advkey);
            row.put("daily_dt",     dateTime);
            row.put("media",        "naverda");
            row.put("target_id",    null);
            row.put("target_name",  null);
            row.put("level",        "account");
            row.put("type",         "account_rate_updown_by_" + kpi);
            row.put("content",      content);
            row.put("daily_regdate", dateTime);

            alarmMapper.insertBudgetAlarm(row);
            log.info("[GFA][BUDGET-ALARM] account alarm 발생 userId={} kpi={} rate={}", userId, kpi, perStr);
        }
    }

    private void campaignBudgetAlarm(String userId, String advkey, Map<String, Object> alarmSetting) {
        List<Map<String, Object>> campaigns = mongoService.selectGfaCampaigns(advkey);
        if (campaigns.isEmpty()) {
            log.warn("[GFA][BUDGET-ALARM] 캠페인 없음 advkey={}", advkey);
            return;
        }

        for (Map<String, Object> campaign : campaigns) {
            String campaignId   = String.valueOf(campaign.get("cid"));
            String campaignName = String.valueOf(campaign.getOrDefault("cname", ""));
            if (toInt(campaign.get("onoff")) == 0) continue; // off 상태 스킵

            for (String kpi : KPIS) {
                String periodStr = getStr(alarmSetting.get(kpi));
                if (periodStr.length() < 2) continue;

                int existingCount = alarmMapper.countRecentAlarm(
                    userId, advkey, "campaign", campaignId, "campaign_rate_updown_by_" + kpi);
                if (existingCount > 0) continue;

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
                double a = mongoService.sumGfaCampaignDaily(advkey, fromDate.toString(), toDate.toString(), col, campaignId);
                double b = mongoService.sumGfaCampaignDaily(advkey, cFromDate.toString(), cToDate.toString(), col, campaignId);

                double per = calculateIncreaseRate(b, a);
                if (per == 0) continue;

                String perStr  = String.format("%.2f", per);
                String content = campaignName + "캠페인의 " + KPI_NAMES.get(kpi) + " 인상률은 " + perStr + "% 입니다.";
                String dateTime = LocalDateTime.now().format(DT_FMT);

                Map<String, Object> row = new LinkedHashMap<>();
                row.put("user_id",      userId);
                row.put("daily_advid",  advkey);
                row.put("daily_dt",     dateTime);
                row.put("media",        "naverda");
                row.put("target_id",    campaignId);
                row.put("target_name",  campaignName);
                row.put("level",        "campaign");
                row.put("type",         "campaign_rate_updown_by_" + kpi);
                row.put("content",      content);
                row.put("daily_regdate", dateTime);

                alarmMapper.insertBudgetAlarm(row);
                log.info("[GFA][BUDGET-ALARM] campaign alarm 발생 userId={} campaignId={} kpi={} rate={}",
                    userId, campaignId, kpi, perStr);
            }
        }
    }

    private double calculateIncreaseRate(double b, double a) {
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
