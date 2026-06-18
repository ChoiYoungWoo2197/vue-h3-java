package com.h3.h3_java.batch.stat;

import com.h3.h3_java.batch.scheduler.KakaoMoTokenManager;
import com.h3.h3_java.media.kakao.KakaoMoApiClient;
import com.h3.h3_java.media.kakao.dto.KakaoMoAccountDto;
import com.h3.h3_java.media.kakao.mapper.KakaoMoMapper;
import com.h3.h3_java.raw.mongo.AccountLogMongoService;
import com.h3.h3_java.raw.mongo.KakaoMoMasterMongoService;
import com.h3.h3_java.raw.mongo.KakaoMoStatMongoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 캠페인 일별 수집.
 * PHP kakaomocampaigndaycollection.php → MongoDB kakao_mo_campaign_daily.
 * MO API: /openapi/v4/campaigns/report, dimension=CREATIVE_FORMAT (5캠페인씩 배치).
 * CREATIVE_FORMAT 다중 행을 campaign_id 기준으로 집계(합산) 후 저장.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KakaoMoCampaignDayJob {

    private final KakaoMoMapper             mapper;
    private final KakaoMoMasterMongoService masterMongo;
    private final KakaoMoStatMongoService   statMongo;
    private final KakaoMoTokenManager       tokenManager;
    private final AccountLogMongoService    accountLogMongo;

    private static final DateTimeFormatter FMT    = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter APIFMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int BATCH_SIZE = 5;

    public void collect() {
        List<KakaoMoAccountDto> accounts = mapper.selectKakaoMoAccounts();
        log.info("[KAKAO-MO][CAMPAIGN-DAY] 전체 수집 시작 accounts={}", accounts.size());
        List<String> dates = buildAutoDates();
        for (KakaoMoAccountDto account : accounts) {
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
        KakaoMoAccountDto account = mapper.selectKakaoMoAccounts().stream()
            .filter(a -> userId.equals(a.getUserId()))
            .findFirst().orElse(null);
        if (account == null) return false;
        collectForAccount(account, dates);
        return true;
    }

    private void collectForAccount(KakaoMoAccountDto account, List<String> dates) {
        String advkey = account.getAccountKakaomoment();
        String token  = tokenManager.getAccessToken();
        if (token == null) {
            log.warn("[KAKAO-MO][CAMPAIGN-DAY][SKIP] 토큰 없음 advkey={}", advkey);
            return;
        }

        KakaoMoApiClient api = new KakaoMoApiClient(token, advkey);
        List<Map<String, Object>> campaigns = masterMongo.findCampaigns(advkey);

        for (String date : dates) {
            collectDate(api, advkey, date, campaigns);
        }

        log.info("[KAKAO-MO][CAMPAIGN-DAY] 완료 advkey={} dates={}", advkey, dates.size());
        accountLogMongo.updateField(advkey, "kakaomo", "campaign");
    }

    @SuppressWarnings("unchecked")
    private void collectDate(KakaoMoApiClient api, String advkey, String date,
                              List<Map<String, Object>> campaigns) {
        if (date.compareTo(LocalDate.now().format(FMT)) >= 0) return;

        String apiDate = LocalDate.parse(date, FMT).format(APIFMT);
        List<String> campaignIds = new ArrayList<>();
        for (Map<String, Object> c : campaigns) {
            String cid = str(c, "cid");
            if (cid != null) campaignIds.add(cid);
        }

        // 5개씩 배치 처리
        for (int i = 0; i < campaignIds.size(); i += BATCH_SIZE) {
            List<String> batch = campaignIds.subList(i, Math.min(i + BATCH_SIZE, campaignIds.size()));
            String batchIds = String.join(",", batch);

            Map<String, String> params = new LinkedHashMap<>();
            params.put("campaignId",   batchIds);
            params.put("metricsGroup", "BASIC,ADDITION,MESSAGE,PIXEL_SDK_CONVERSION");
            params.put("start",        apiDate);
            params.put("end",          apiDate);
            params.put("timeUnit",     "DAY");
            params.put("level",        "CAMPAIGN");
            params.put("dimension",    "CREATIVE_FORMAT");

            Map<String, Object> res = api.get("/openapi/v4/campaigns/report", params);
            if (res == null) continue;

            Object dataObj = res.get("data");
            if (!(dataObj instanceof List)) continue;

            // CREATIVE_FORMAT으로 복수 행 → campaign_id 기준 집계
            Map<String, long[]> agg = new LinkedHashMap<>();
            String rowDate = date;

            for (Map<String, Object> row : (List<Map<String, Object>>) dataObj) {
                Map<String, Object> dims    = (Map<String, Object>) row.get("dimensions");
                Map<String, Object> metrics = (Map<String, Object>) row.get("metrics");
                if (dims == null || metrics == null) continue;

                String campaignId = str(dims, "campaign_id");
                if (campaignId == null) continue;

                String rd = str(row, "start");
                if (rd != null) rowDate = rd;

                long[] sums = agg.computeIfAbsent(campaignId, k -> new long[5]);
                sums[0] += longVal(metrics, "imp");
                sums[1] += longVal(metrics, "click");
                sums[2] += longVal(metrics, "cost");
                sums[3] += longVal(metrics, "conv_purchase_1d") + longVal(metrics, "conv_signup_1d");
                sums[4] += longVal(metrics, "conv_purchase_p_1d");
            }

            for (Map.Entry<String, long[]> entry : agg.entrySet()) {
                String campaignId = entry.getKey();
                long[] s = entry.getValue();

                if (s[0] == 0 && s[1] == 0 && s[2] == 0 && s[3] == 0 && s[4] == 0) continue;
                if (statMongo.existsCampaignDaily(advkey, rowDate, campaignId)) continue;

                Map<String, Object> doc = new HashMap<>();
                doc.put("advkey",      advkey);
                doc.put("daily_dt",    rowDate);
                doc.put("campaign_id", campaignId);
                doc.put("daily_im",    s[0]);
                doc.put("daily_clk",   s[1]);
                doc.put("daily_cst",   s[2]);
                doc.put("daily_cv",    s[3]);
                doc.put("daily_cr",    s[4]);
                statMongo.insertCampaignDaily(doc);
            }

            // OFF 캠페인 0으로 채우기
            for (String cid : batch) {
                Map<String, Object> cm = campaigns.stream()
                    .filter(c -> cid.equals(str(c, "cid"))).findFirst().orElse(null);
                if (cm == null) continue;
                int onoff = cm.get("onoff") instanceof Number ? ((Number) cm.get("onoff")).intValue() : 0;
                if (onoff == 1) continue;
                if (statMongo.existsCampaignDaily(advkey, date, cid)) continue;

                Map<String, Object> doc = new HashMap<>();
                doc.put("advkey",      advkey);
                doc.put("daily_dt",    date);
                doc.put("campaign_id", cid);
                doc.put("daily_im",    0L);
                doc.put("daily_clk",   0L);
                doc.put("daily_cst",   0L);
                doc.put("daily_cv",    0L);
                doc.put("daily_cr",    0L);
                statMongo.insertCampaignDaily(doc);
            }

            sleep(5_000);
        }
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

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
