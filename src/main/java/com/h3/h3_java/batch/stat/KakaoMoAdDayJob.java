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
 * 소재 일별 수집.
 * PHP kakaomoaddaycollection.php → MongoDB kakao_mo_ad_daily.
 * MO API: /openapi/v4/creatives/report, dimension=CREATIVE_FORMAT (100소재씩 배치).
 * ad_id = creative.id (dimensions.creative_id).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KakaoMoAdDayJob {

    private final KakaoMoMapper             mapper;
    private final KakaoMoMasterMongoService masterMongo;
    private final KakaoMoStatMongoService   statMongo;
    private final KakaoMoTokenManager       tokenManager;
    private final AccountLogMongoService    accountLogMongo;

    private static final DateTimeFormatter FMT    = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter APIFMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int BATCH_SIZE  = 100;
    private static final int MAX_PER_DAY = 5000;

    public void collect() {
        List<KakaoMoAccountDto> accounts = mapper.selectKakaoMoAccounts();
        log.info("[KAKAO-MO][AD-DAY] 전체 수집 시작 accounts={}", accounts.size());
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
            log.warn("[KAKAO-MO][AD-DAY][SKIP] 토큰 없음 advkey={}", advkey);
            return;
        }

        KakaoMoApiClient api = new KakaoMoApiClient(token, advkey);
        List<Map<String, Object>> adgroups = masterMongo.findAdGroups(advkey);

        for (String date : dates) {
            collectDate(api, advkey, date, adgroups);
        }

        log.info("[KAKAO-MO][AD-DAY] 완료 advkey={} dates={}", advkey, dates.size());
        accountLogMongo.updateField(advkey, "kakaomo", "ad");
    }

    @SuppressWarnings("unchecked")
    private void collectDate(KakaoMoApiClient api, String advkey, String date,
                              List<Map<String, Object>> adgroups) {
        if (date.compareTo(LocalDate.now().format(FMT)) >= 0) return;

        String apiDate = LocalDate.parse(date, FMT).format(APIFMT);
        int cnt = 0;

        // 1단계: 전체 adgroup의 소재를 한 번에 수집
        List<String> allAdIds = new ArrayList<>();
        Map<String, String> aidToGid = new HashMap<>();
        Map<String, String> aidToCid = new HashMap<>();

        for (Map<String, Object> ag : adgroups) {
            String gid = str(ag, "gid");
            String cid = str(ag, "cid");
            if (gid == null) continue;

            List<Map<String, Object>> creatives = api.getContent(
                "/openapi/v4/creatives",
                Map.of("adGroupId", gid)
            );
            if (creatives == null || creatives.isEmpty()) continue;

            for (Map<String, Object> c : creatives) {
                String aid = str(c, "id");
                if (aid == null) continue;
                allAdIds.add(aid);
                aidToGid.put(aid, gid);
                aidToCid.put(aid, cid);
            }
        }

        // 2단계: 전체 소재를 100개씩 배치 처리 (sleep은 배치 단위로만)
        for (int i = 0; i < allAdIds.size(); i += BATCH_SIZE) {
            if (cnt >= MAX_PER_DAY) break;

            List<String> batch = allAdIds.subList(i, Math.min(i + BATCH_SIZE, allAdIds.size()));
            String batchIds = String.join(",", batch);

            Map<String, String> params = new LinkedHashMap<>();
            params.put("creativeId",   batchIds);
            params.put("metricsGroup", "BASIC,ADDITION,MESSAGE,PIXEL_SDK_CONVERSION");
            params.put("start",        apiDate);
            params.put("end",          apiDate);
            params.put("timeUnit",     "DAY");
            params.put("dimension",    "CREATIVE_FORMAT");

            sleep(5_000);
            Map<String, Object> res = api.get("/openapi/v4/creatives/report", params);
            if (res == null) continue;

            Object dataObj = res.get("data");
            if (!(dataObj instanceof List)) continue;

            // creative_id 기준 집계
            Map<String, long[]> agg = new LinkedHashMap<>();
            String rowDate = date;

            for (Map<String, Object> row : (List<Map<String, Object>>) dataObj) {
                Map<String, Object> dims    = (Map<String, Object>) row.get("dimensions");
                Map<String, Object> metrics = (Map<String, Object>) row.get("metrics");
                if (dims == null || metrics == null) continue;

                String adId = str(dims, "creative_id");
                if (adId == null) continue;

                String rd = str(row, "start");
                if (rd != null) rowDate = rd;

                long[] sums = agg.computeIfAbsent(adId, k -> new long[5]);
                sums[0] += longVal(metrics, "imp");
                sums[1] += longVal(metrics, "click");
                sums[2] += longVal(metrics, "cost");
                sums[3] += longVal(metrics, "conv_purchase_1d") + longVal(metrics, "conv_signup_1d");
                sums[4] += longVal(metrics, "conv_purchase_p_1d");
            }

            for (Map.Entry<String, long[]> entry : agg.entrySet()) {
                if (cnt >= MAX_PER_DAY) break;
                String adId = entry.getKey();
                long[] s = entry.getValue();

                if (s[0] == 0 && s[1] == 0 && s[2] == 0 && s[3] == 0 && s[4] == 0) continue;
                if (statMongo.existsAdDaily(advkey, rowDate, adId)) continue;

                Map<String, Object> doc = new HashMap<>();
                doc.put("advkey",      advkey);
                doc.put("daily_dt",    rowDate);
                doc.put("campaign_id", aidToCid.get(adId));
                doc.put("adgroup_id",  aidToGid.get(adId));
                doc.put("ad_id",       adId);
                doc.put("daily_im",    s[0]);
                doc.put("daily_clk",   s[1]);
                doc.put("daily_cst",   s[2]);
                doc.put("daily_cv",    s[3]);
                doc.put("daily_cr",    s[4]);
                statMongo.insertAdDaily(doc);
                cnt++;
            }
        }

        log.debug("[KAKAO-MO][AD-DAY] 저장 advkey={} date={} saved={}", advkey, date, cnt);
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
