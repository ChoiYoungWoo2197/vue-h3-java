package com.h3.h3_java.batch.stat;

import com.h3.h3_java.batch.scheduler.KakaoMoTokenManager;
import com.h3.h3_java.media.kakao.KakaoMoApiClient;
import com.h3.h3_java.media.kakao.dto.KakaoMoAccountDto;
import com.h3.h3_java.media.kakao.mapper.KakaoMoMapper;
import com.h3.h3_java.media.kakao.mapper.KakaoMoBudgetAlarmMapper;
import com.h3.h3_java.raw.mongo.KakaoMoStatMongoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Kakao MO 예산 알람 수집.
 * PHP kakaomobudgetalarmcollection.php → MongoDB kakao_mo_budget_alarm.
 * 알람 설정은 MySQL h3_budget_daily_alarm에서 읽고, 실적은 MongoDB kakao_mo_campaign_daily에서 읽음.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KakaoMoBudgetAlarmJob {

    private final KakaoMoMapper           mapper;
    private final KakaoMoBudgetAlarmMapper alarmMapper;
    private final KakaoMoStatMongoService  statMongo;
    private final KakaoMoTokenManager      tokenManager;

    private static final DateTimeFormatter FMT  = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final List<String>      KPIS = List.of("im", "clk", "cv", "cr");

    public void collect() {
        List<KakaoMoAccountDto> accounts = mapper.selectKakaoMoAccounts();
        log.info("[KAKAO-MO][BUDGET-ALARM] 수집 시작 accounts={}", accounts.size());
        for (KakaoMoAccountDto account : accounts) {
            collectForAccount(account);
        }
    }

    public boolean collectForUserId(String userId) {
        KakaoMoAccountDto account = mapper.selectKakaoMoAccounts().stream()
            .filter(a -> userId.equals(a.getUserId()))
            .findFirst().orElse(null);
        if (account == null) return false;
        collectForAccount(account);
        return true;
    }

    private void collectForAccount(KakaoMoAccountDto account) {
        String userId = account.getUserId();
        String advkey = account.getAccountKakaomoment();
        String token  = tokenManager.getAccessToken();
        if (token == null) {
            log.warn("[KAKAO-MO][BUDGET-ALARM][SKIP] 토큰 없음 advkey={}", advkey);
            return;
        }

        KakaoMoApiClient api = new KakaoMoApiClient(token, advkey);

        Map<String, Object> alarmSet = alarmMapper.selectBudgetAlarm(userId);

        // 캠페인/계정 예산 알람 (알람 설정 있을 때)
        if (alarmSet != null) {
            String alarmType = alarmMapper.selectAlarmType(userId);
            if (alarmType != null) {
                accountBudgetAlarm(userId, advkey, alarmSet);
            } else {
                campaignBudgetAlarm(api, userId, advkey, alarmSet);
            }
        }

        log.info("[KAKAO-MO][BUDGET-ALARM] 완료 advkey={}", advkey);
    }

    private void accountBudgetAlarm(String userId, String advkey, Map<String, Object> alarmSet) {
        for (String kpi : KPIS) {
            String setting = alarmSet.get(kpi) != null ? alarmSet.get(kpi).toString() : null;
            if (setting == null || setting.isEmpty()) continue;

            String type = "account_rate_updown_by_" + kpi;
            Query checkQ = Query.query(
                Criteria.where("user_id").is(userId)
                    .and("advkey").is(advkey)
                    .and("daily_dt").gte(LocalDate.now().minusDays(7).format(FMT))
                    .and("media").is("kakaomo")
                    .and("level").is("account")
                    .and("type").is(type)
            );
            if (statMongo.countBudgetAlarm(checkQ) > 0) continue;

            int day       = Integer.parseInt(setting.substring(1)) - 1;
            String toDate   = LocalDate.now().minusDays(1).format(FMT);
            String fromDate = LocalDate.now().minusDays(1 + day).format(FMT);
            String ctoDate  = LocalDate.now().minusDays(2 + day).format(FMT);
            String cfromDate = LocalDate.now().minusDays(2 + day * 2L).format(FMT);

            long a = statMongo.sumAccountDailyStat(advkey, "daily_" + kpi, fromDate, toDate);
            long b = statMongo.sumAccountDailyStat(advkey, "daily_" + kpi, cfromDate, ctoDate);

            double per = calculateRate(b, a);
            if (per == 0) continue;

            String kpiName = kpiName(kpi);
            String content = advkey + "계정의 " + kpiName + " 인상률은 " + String.format("%.2f", per) + "% 입니다.";
            insertAlarm(userId, advkey, "account", null, null, type, content);
        }
    }

    @SuppressWarnings("unchecked")
    private void campaignBudgetAlarm(KakaoMoApiClient api, String userId, String advkey,
                                      Map<String, Object> alarmSet) {
        List<Map<String, Object>> campaigns = api.getContent("/openapi/v4/campaigns", null);
        if (campaigns == null) return;

        for (Map<String, Object> campaign : campaigns) {
            String cid   = str(campaign, "id");
            String cname = str(campaign, "name");
            String onoff = str(campaign, "config");
            if (cid == null || "OFF".equals(onoff)) continue;

            // KPI 알람
            for (String kpi : KPIS) {
                String setting = alarmSet.get(kpi) != null ? alarmSet.get(kpi).toString() : null;
                if (setting == null || setting.isEmpty()) continue;

                String type = "campaign_rate_updown_by_" + kpi;
                Query checkQ = Query.query(
                    Criteria.where("user_id").is(userId)
                        .and("advkey").is(advkey)
                        .and("daily_dt").gte(LocalDate.now().minusDays(7).format(FMT))
                        .and("media").is("kakaomo")
                        .and("target_id").is(cid)
                        .and("level").is("campaign")
                        .and("type").is(type)
                );
                if (statMongo.countBudgetAlarm(checkQ) > 0) continue;

                int day       = Integer.parseInt(setting.substring(1)) - 1;
                String toDate   = LocalDate.now().minusDays(1).format(FMT);
                String fromDate = LocalDate.now().minusDays(1 + day).format(FMT);
                String ctoDate  = LocalDate.now().minusDays(2 + day).format(FMT);
                String cfromDate = LocalDate.now().minusDays(2 + day * 2L).format(FMT);

                long a = statMongo.sumCampaignDailyStat(advkey, cid, "daily_" + kpi, fromDate, toDate);
                long b = statMongo.sumCampaignDailyStat(advkey, cid, "daily_" + kpi, cfromDate, ctoDate);

                double per = calculateRate(b, a);
                if (per == 0) continue;

                String kpiName = kpiName(kpi);
                String content = cname + "캠페인의 " + kpiName + " 인상률은 " + String.format("%.2f", per) + "% 입니다.";
                insertAlarm(userId, advkey, "campaign", cid, cname, type, content);
            }
        }
    }

    private void insertAlarm(String userId, String advkey, String level,
                              String targetId, String targetName,
                              String type, String content) {
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        Map<String, Object> doc = new HashMap<>();
        doc.put("user_id",     userId);
        doc.put("advkey",      advkey);
        doc.put("daily_dt",    now);
        doc.put("media",       "kakaomo");
        doc.put("target_id",   targetId);
        doc.put("target_name", targetName);
        doc.put("level",       level);
        doc.put("type",        type);
        doc.put("content",     content);
        statMongo.insertBudgetAlarm(doc);
        log.info("[KAKAO-MO][BUDGET-ALARM] 알람 저장 advkey={} type={}", advkey, type);
    }

    private double calculateRate(long before, long after) {
        if (before == 0) return 0;
        return ((double)(after - before) / before) * 100.0;
    }

    private String kpiName(String kpi) {
        return switch (kpi) {
            case "im"  -> "총노출수";
            case "clk" -> "총클릭수";
            case "cv"  -> "총전환수";
            case "cr"  -> "총전환매출";
            default    -> kpi;
        };
    }

    private String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v != null ? String.valueOf(v) : null;
    }
}
