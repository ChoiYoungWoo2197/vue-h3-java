package com.h3.h3_java.batch.stat;

import com.h3.h3_java.batch.scheduler.KakaoSaTokenManager;
import com.h3.h3_java.media.kakao.KakaoSaApiClient;
import com.h3.h3_java.media.kakao.dto.KakaoSaAccountDto;
import com.h3.h3_java.raw.mongo.AccountMongoService;
import com.h3.h3_java.raw.mongo.AccountLogMongoService;
import com.h3.h3_java.raw.mongo.KakaoSaMasterMongoService;
import com.h3.h3_java.raw.mongo.KakaoSaStatMongoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class KakaoSaAdGroupDayJob {

    private final AccountMongoService       accountMongo;
    private final KakaoSaMasterMongoService masterMongo;
    private final KakaoSaStatMongoService   statMongo;
    private final KakaoSaTokenManager       tokenManager;
    private final AccountLogMongoService    accountLogMongo;

    private static final DateTimeFormatter FMT    = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter APIFMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    public void collect() {
        List<KakaoSaAccountDto> accounts = accountMongo.findKakaoSaAccountDtos();
        log.info("[KAKAO-SA][ADGROUP-DAY] 전체 수집 시작 accounts={}", accounts.size());
        List<String> dates = buildAutoDates();
        for (KakaoSaAccountDto account : accounts) {
            collectForAccount(account, dates);
        }
    }

    public boolean collectForUserId(String userId) {
        return runForUser(userId, buildAutoDates());
    }

    public boolean collectRange(String userId, String fromDate, String toDate) {
        return runForUser(userId, buildDateRange(fromDate, toDate));
    }

    private boolean runForUser(String userId, List<String> dates) {
        KakaoSaAccountDto account = accountMongo.findKakaoSaAccountDtos().stream()
            .filter(a -> userId.equals(a.getUserId()))
            .findFirst().orElse(null);
        if (account == null) return false;
        collectForAccount(account, dates);
        return true;
    }

    private void collectForAccount(KakaoSaAccountDto account, List<String> dates) {
        String advkey = account.getAccountKakaosa();
        String token  = tokenManager.getAccessToken();
        if (token == null) {
            log.warn("[KAKAO-SA][ADGROUP-DAY][SKIP] 토큰 없음 advkey={}", advkey);
            return;
        }

        KakaoSaApiClient api = new KakaoSaApiClient(token, advkey);
        List<Map<String, Object>> campaigns = masterMongo.findCampaigns(advkey);
        List<Map<String, Object>> adgroups  = masterMongo.findAdGroups(advkey);

        for (String date : dates) {
            int saved = collectDate(api, advkey, date, campaigns, adgroups);
            log.info("[KAKAO-SA][ADGROUP-DAY] date={} saved={} advkey={}", date, saved, advkey);
        }

        log.info("[KAKAO-SA][ADGROUP-DAY] 완료 advkey={} dates={}", advkey, dates.size());
        accountLogMongo.updateField(advkey, "kakaosa", "adgroup");
    }

    @SuppressWarnings("unchecked")
    private int collectDate(KakaoSaApiClient api, String advkey, String date,
                              List<Map<String, Object>> campaigns,
                              List<Map<String, Object>> adgroups) {
        if (date.compareTo(LocalDate.now().format(FMT)) >= 0) return 0;

        String apiDate = LocalDate.parse(date, FMT).format(APIFMT);
        int saved = 0;

        for (Map<String, Object> campaign : campaigns) {
            String cid = str(campaign, "cid");
            if (cid == null) continue;

            Map<String, String> params = new LinkedHashMap<>();
            params.put("campaignId",    cid);
            params.put("metricsGroups", "BASIC,ADDITION,PIXEL_SDK_CONVERSION");
            params.put("start",         apiDate);
            params.put("end",           apiDate);

            Map<String, Object> res = api.get("/openapi/v1/adGroups/report", params);
            if (res == null) continue;

            Object dataObj = res.get("data");
            if (!(dataObj instanceof List)) continue;

            for (Map<String, Object> row : (List<Map<String, Object>>) dataObj) {
                Map<String, Object> dims    = (Map<String, Object>) row.get("dimensions");
                Map<String, Object> metrics = (Map<String, Object>) row.get("metrics");
                if (dims == null || metrics == null) continue;

                String adgroupId = str(dims, "adGroupId");
                String rowDate   = str(row, "start");
                String actualDate = rowDate != null ? rowDate : date;
                if (adgroupId == null) continue;

                long clk = longVal(metrics, "click");
                long im  = longVal(metrics, "imp");
                long cst = longVal(metrics, "spending");
                long cv  = longVal(metrics, "convPurchase1d") + longVal(metrics, "convSignup1d");
                long cr  = longVal(metrics, "convPurchaseP1d");

                if (clk == 0 && im == 0 && cst == 0 && cv == 0 && cr == 0) continue;
                if (statMongo.existsAdGroupDaily(advkey, actualDate, adgroupId)) continue;

                Map<String, Object> doc = new HashMap<>();
                doc.put("advkey",     advkey);
                doc.put("daily_dt",   actualDate);
                doc.put("campaign_id", cid);
                doc.put("adgroup_id", adgroupId);
                doc.put("daily_im",   im);
                doc.put("daily_clk",  clk);
                doc.put("daily_cst",  cst);
                doc.put("daily_cv",   cv);
                doc.put("daily_cr",   cr);
                statMongo.insertAdGroupDaily(doc);
                saved++;
            }
        }

        // OFF 광고그룹 0으로 채우기
        for (Map<String, Object> ag : adgroups) {
            int onoff = ag.get("onoff") instanceof Number ? ((Number) ag.get("onoff")).intValue() : 0;
            if (onoff == 1) continue;
            String gid = str(ag, "gid");
            String cid = str(ag, "cid");
            if (gid == null) continue;
            if (statMongo.existsAdGroupDaily(advkey, date, gid)) continue;

            Map<String, Object> doc = new HashMap<>();
            doc.put("advkey",     advkey);
            doc.put("daily_dt",   date);
            doc.put("campaign_id", cid);
            doc.put("adgroup_id", gid);
            doc.put("daily_im",   0L);
            doc.put("daily_clk",  0L);
            doc.put("daily_cst",  0L);
            doc.put("daily_cv",   0L);
            doc.put("daily_cr",   0L);
            statMongo.insertAdGroupDaily(doc);
            saved++;
        }

        return saved;
    }

    private List<String> buildAutoDates() {
        Set<String> dates = new LinkedHashSet<>();
        LocalDate today = LocalDate.now();
        for (int d : new int[]{1, 3, 5}) {
            dates.add(today.minusDays(d).format(FMT));
        }
        return new ArrayList<>(dates);
    }

    private List<String> buildDateRange(String from, String to) {
        List<String> dates = new ArrayList<>();
        LocalDate cur = LocalDate.parse(from, FMT);
        LocalDate end = LocalDate.parse(to, FMT);
        while (!cur.isAfter(end)) {
            dates.add(cur.format(FMT));
            cur = cur.plusDays(1);
        }
        return dates;
    }

    private long longVal(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v instanceof Number ? ((Number) v).longValue() : 0L;
    }

    private String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v != null ? String.valueOf(v) : null;
    }
}
