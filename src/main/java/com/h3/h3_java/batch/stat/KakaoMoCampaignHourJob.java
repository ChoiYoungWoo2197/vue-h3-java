package com.h3.h3_java.batch.stat;

import com.h3.h3_java.batch.scheduler.KakaoMoTokenManager;
import com.h3.h3_java.media.kakao.KakaoMoApiClient;
import com.h3.h3_java.media.kakao.dto.KakaoMoAccountDto;
import com.h3.h3_java.media.kakao.mapper.KakaoMoMapper;
import com.h3.h3_java.raw.mongo.AccountLogMongoService;
import com.h3.h3_java.raw.mongo.KakaoMoStatMongoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 캠페인 시간별 수집.
 * PHP kakaomocampaignhourcollection.php → MongoDB kakao_mo_campaign_hour.
 * MO API: dimension=HOUR, 시간키 "00:00~00:59" → 0.
 * 전체 캠페인 시간 데이터를 계정 단위로 합산 후 hours 배열로 저장.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KakaoMoCampaignHourJob {

    private final KakaoMoMapper           mapper;
    private final KakaoMoStatMongoService statMongo;
    private final KakaoMoTokenManager     tokenManager;
    private final AccountLogMongoService  accountLogMongo;

    private static final DateTimeFormatter FMT    = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter APIFMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int BATCH_SIZE = 5;

    private static final Map<String, Integer> HOUR_SET = buildHourSet();

    private static Map<String, Integer> buildHourSet() {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (int h = 0; h < 24; h++) {
            String key = String.format("%02d:00~%02d:59", h, h);
            m.put(key, h);
        }
        return m;
    }

    public void collect() {
        List<KakaoMoAccountDto> accounts = mapper.selectKakaoMoAccounts();
        log.info("[KAKAO-MO][CAMPAIGN-HOUR] 전체 수집 시작 accounts={}", accounts.size());
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
            log.warn("[KAKAO-MO][CAMPAIGN-HOUR][SKIP] 토큰 없음 advkey={}", advkey);
            return;
        }

        KakaoMoApiClient api = new KakaoMoApiClient(token, advkey);

        // 캠페인 목록
        List<String> campaignIds = new ArrayList<>();
        {
            List<Map<String, Object>> campaigns = api.getContent("/openapi/v4/campaigns", null);
            if (campaigns != null) {
                for (Map<String, Object> c : campaigns) {
                    String cid = str(c, "id");
                    if (cid != null) campaignIds.add(cid);
                }
            }
        }

        for (String date : dates) {
            int saved = collectDate(api, advkey, date, campaignIds);
            log.info("[KAKAO-MO][CAMPAIGN-HOUR] date={} saved={} advkey={}", date, saved, advkey);
        }

        log.info("[KAKAO-MO][CAMPAIGN-HOUR] 완료 advkey={} dates={}", advkey, dates.size());
        accountLogMongo.updateField(advkey, "kakaomo", "campaign_hour");
    }

    @SuppressWarnings("unchecked")
    private int collectDate(KakaoMoApiClient api, String advkey, String date,
                              List<String> campaignIds) {
        if (date.compareTo(LocalDate.now().format(FMT)) >= 0) return 0;
        if (statMongo.existsCampaignHour(advkey, date)) return 0;

        String apiDate = LocalDate.parse(date, FMT).format(APIFMT);

        long[] im  = new long[24];
        long[] clk = new long[24];
        long[] cst = new long[24];
        long[] cv  = new long[24];
        long[] cr  = new long[24];

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
            params.put("dimension",    "HOUR");

            Map<String, Object> res = api.get("/openapi/v4/campaigns/report", params);
            if (res == null) { sleep(5_000); continue; }

            Object dataObj = res.get("data");
            if (!(dataObj instanceof List)) { sleep(5_000); continue; }

            for (Map<String, Object> row : (List<Map<String, Object>>) dataObj) {
                Map<String, Object> dims    = (Map<String, Object>) row.get("dimensions");
                Map<String, Object> metrics = (Map<String, Object>) row.get("metrics");
                if (dims == null || metrics == null) continue;

                String hourKey = str(dims, "hour");
                if (hourKey == null) continue;
                Integer h = HOUR_SET.get(hourKey);
                if (h == null || h < 0 || h > 23) continue;

                im[h]  += longVal(metrics, "imp");
                clk[h] += longVal(metrics, "click");
                cst[h] += longVal(metrics, "cost");
                cv[h]  += longVal(metrics, "conv_purchase_1d") + longVal(metrics, "conv_signup_1d");
                cr[h]  += longVal(metrics, "conv_purchase_p_1d");
            }

            sleep(5_000);
        }

        List<Map<String, Object>> hours = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            Map<String, Object> hourDoc = new HashMap<>();
            hourDoc.put("hour", h);
            hourDoc.put("im",   im[h]);
            hourDoc.put("clk",  clk[h]);
            hourDoc.put("cst",  cst[h]);
            hourDoc.put("cv",   cv[h]);
            hourDoc.put("cr",   cr[h]);
            hours.add(hourDoc);
        }

        Map<String, Object> doc = new HashMap<>();
        doc.put("advkey",  advkey);
        doc.put("hour_dt", date);
        doc.put("hours",   hours);
        statMongo.insertCampaignHour(doc);

        return 1;
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
