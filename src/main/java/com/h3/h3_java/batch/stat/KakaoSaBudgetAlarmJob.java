package com.h3.h3_java.batch.stat;

import com.h3.h3_java.batch.scheduler.KakaoSaTokenManager;
import com.h3.h3_java.media.kakao.KakaoSaApiClient;
import com.h3.h3_java.media.kakao.dto.KakaoSaAccountDto;
import com.h3.h3_java.raw.mongo.AccountMongoService;
import com.h3.h3_java.raw.mongo.BudgetAlarmMongoService;
import com.h3.h3_java.raw.mongo.KakaoSaStatMongoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class KakaoSaBudgetAlarmJob {

    private final AccountMongoService     accountMongo;
    private final BudgetAlarmMongoService budgetAlarmMongo;
    private final KakaoSaStatMongoService statMongo;
    private final KakaoSaTokenManager     tokenManager;

    private static final DateTimeFormatter FMT    = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter APIFMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final List<String>      KPIS   = List.of("im", "clk", "cv", "cr");

    public void collect() {
        List<KakaoSaAccountDto> accounts = accountMongo.findKakaoSaAccountDtos();
        log.info("[KAKAO-SA][BUDGET-ALARM] 수집 시작 accounts={}", accounts.size());
        for (KakaoSaAccountDto account : accounts) {
            collectForAccount(account);
        }
    }

    public boolean collectForUserId(String userId) {
        KakaoSaAccountDto account = accountMongo.findKakaoSaAccountDtos().stream()
            .filter(a -> userId.equals(a.getUserId()))
            .findFirst().orElse(null);
        if (account == null) return false;
        collectForAccount(account);
        return true;
    }

    private void collectForAccount(KakaoSaAccountDto account) {
        String userId = account.getUserId();
        String advkey = account.getAccountKakaosa();
        String token  = tokenManager.getAccessToken();
        if (token == null) {
            log.warn("[KAKAO-SA][BUDGET-ALARM][SKIP] 토큰 없음 advkey={}", advkey);
            return;
        }

        KakaoSaApiClient api = new KakaoSaApiClient(token, advkey);

        // 알람 설정 조회 (MongoDB)
        Document alarmDoc = budgetAlarmMongo.findSetting(userId);

        // 1. 비즈머니 잔액 부족 알람
        bizmoneyLockAlarm(api, userId, advkey);

        // 2. 캠페인/계정 예산 알람 (알람 설정 있을 때)
        if (alarmDoc != null) {
            Map<String, Object> alarmSet = new HashMap<>(alarmDoc);
            Object typeVal = alarmDoc.get("type");
            // type 필드가 존재하면 계정 레벨, 없으면(null) 캠페인 레벨
            if (typeVal != null) {
                accountBudgetAlarm(userId, advkey, alarmSet);
            } else {
                campaignBudgetAlarm(api, userId, advkey, alarmSet);
            }
        }

        log.info("[KAKAO-SA][BUDGET-ALARM] 완료 advkey={}", advkey);
    }

    /** 비즈머니 잔액 부족 알람 */
    @SuppressWarnings("unchecked")
    private void bizmoneyLockAlarm(KakaoSaApiClient api, String userId, String advkey) {
        Map<String, Object> res = api.get("/openapi/v1/adAccounts/" + advkey);
        if (res == null) return;

        Object outOfBalance = res.get("outOfBalance");
        Object statusDesc   = res.get("statusDescription");
        if (!(outOfBalance instanceof Boolean) || !(Boolean) outOfBalance) return;
        if (statusDesc == null || !statusDesc.toString().contains("잔액부족")) return;

        String type = "bizmoney_locked_by_budget";
        Query q = Query.query(
            Criteria.where("user_id").is(userId)
                .and("advkey").is(advkey)
                .and("daily_dt").gte(LocalDate.now().minusDays(1).format(FMT))
                .and("media").is("kakaosa")
                .and("type").is(type)
        );
        if (statMongo.countBudgetAlarm(q) > 0) return;

        String content = advkey + " 계정 비즈머니 잔액 부족으로 계정잠김 상태가 되어 모든 광고 노출이 중지됐습니다.";
        insertAlarm(userId, advkey, "bizmoney", null, null, type, content);
    }

    /** 계정 레벨 KPI 알람 */
    private void accountBudgetAlarm(String userId, String advkey, Map<String, Object> alarmSet) {
        for (String kpi : KPIS) {
            String setting = alarmSet.get(kpi) != null ? alarmSet.get(kpi).toString() : null;
            if (setting == null || setting.isEmpty()) continue;

            String type = "account_rate_updown_by_" + kpi;
            Query checkQ = Query.query(
                Criteria.where("user_id").is(userId)
                    .and("advkey").is(advkey)
                    .and("daily_dt").gte(LocalDate.now().minusDays(7).format(FMT))
                    .and("media").is("kakaosa")
                    .and("level").is("account")
                    .and("type").is(type)
            );
            if (statMongo.countBudgetAlarm(checkQ) > 0) continue;

            int  day     = Integer.parseInt(setting.substring(1)) - 1;
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

    /** 캠페인 레벨 예산 및 KPI 알람 */
    @SuppressWarnings("unchecked")
    private void campaignBudgetAlarm(KakaoSaApiClient api, String userId, String advkey,
                                      Map<String, Object> alarmSet) {
        List<Map<String, Object>> campaigns = api.getList("/openapi/v1/campaigns", null);
        if (campaigns == null) return;

        for (Map<String, Object> campaign : campaigns) {
            String cid   = str(campaign, "id");
            String cname = str(campaign, "name");
            String onoff = str(campaign, "config");
            if (cid == null || "OFF".equals(onoff)) continue;

            // 하루예산 초과 알람
            Map<String, Object> detail = api.get("/openapi/v1/campaigns/" + cid);
            if (detail != null) {
                Object over = detail.get("dailyBudgetAmountOver");
                boolean overFlag = over instanceof Boolean && (Boolean) over;
                boolean statusReason = false;
                Object statusList = detail.get("status");
                if (statusList instanceof List) {
                    for (Object s : (List<?>) statusList) {
                        if (s != null && s.toString().contains("OFF_BY_CAMPAIGN_DAILY_BUDGET")) {
                            statusReason = true;
                            break;
                        }
                    }
                }

                if (overFlag && statusReason) {
                    Query q = Query.query(
                        Criteria.where("user_id").is(userId)
                            .and("advkey").is(advkey)
                            .and("daily_dt").gte(LocalDate.now().minusDays(7).format(FMT))
                            .and("media").is("kakaosa")
                            .and("target_id").is(cid)
                    );
                    if (statMongo.countBudgetAlarm(q) == 0) {
                        String content = cname + " 캠페인 하루예산이 초과 혹은 초과 예상되어 캠페인이 중지되었습니다.";
                        insertAlarm(userId, advkey, "campaign", cid, cname, "campaign_limited_by_budget", content);
                    }
                }
            }

            // KPI 알람
            for (String kpi : KPIS) {
                String setting = alarmSet.get(kpi) != null ? alarmSet.get(kpi).toString() : null;
                if (setting == null || setting.isEmpty()) continue;

                String type = "campaign_rate_updown_by_" + kpi;
                Query checkQ = Query.query(
                    Criteria.where("user_id").is(userId)
                        .and("advkey").is(advkey)
                        .and("daily_dt").gte(LocalDate.now().minusDays(7).format(FMT))
                        .and("media").is("kakaosa")
                        .and("target_id").is(cid)
                        .and("level").is("campaign")
                        .and("type").is(type)
                );
                if (statMongo.countBudgetAlarm(checkQ) > 0) continue;

                int  day     = Integer.parseInt(setting.substring(1)) - 1;
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
        doc.put("media",       "kakaosa");
        doc.put("target_id",   targetId);
        doc.put("target_name", targetName);
        doc.put("level",       level);
        doc.put("type",        type);
        doc.put("content",     content);
        statMongo.insertBudgetAlarm(doc);
        log.info("[KAKAO-SA][BUDGET-ALARM] 알람 저장 advkey={} type={}", advkey, type);
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
