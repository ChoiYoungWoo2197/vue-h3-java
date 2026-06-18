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
 * 광고그룹 일별 수집.
 * PHP kakaomoadgroupdaycollection.php → MongoDB kakao_mo_adgroup_daily.
 * MO API: /openapi/v4/adGroups/report, dimension=CREATIVE_FORMAT (20그룹씩 배치).
 * ad_group_id 기준으로 집계 후 저장.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KakaoMoAdGroupDayJob {

    private final KakaoMoMapper             mapper;
    private final KakaoMoMasterMongoService masterMongo;
    private final KakaoMoStatMongoService   statMongo;
    private final KakaoMoTokenManager       tokenManager;
    private final AccountLogMongoService    accountLogMongo;

    private static final DateTimeFormatter FMT    = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter APIFMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int BATCH_SIZE = 20;

    public void collect() {
        List<KakaoMoAccountDto> accounts = mapper.selectKakaoMoAccounts();
        log.info("[KAKAO-MO][ADGROUP-DAY] 전체 수집 시작 accounts={}", accounts.size());
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
            log.warn("[KAKAO-MO][ADGROUP-DAY][SKIP] 토큰 없음 advkey={}", advkey);
            return;
        }

        KakaoMoApiClient api = new KakaoMoApiClient(token, advkey);
        List<Map<String, Object>> campaigns = masterMongo.findCampaigns(advkey);
        List<Map<String, Object>> adgroups  = masterMongo.findAdGroups(advkey);

        for (String date : dates) {
            collectDate(api, advkey, date, campaigns, adgroups);
        }

        log.info("[KAKAO-MO][ADGROUP-DAY] 완료 advkey={} dates={}", advkey, dates.size());
        accountLogMongo.updateField(advkey, "kakaomo", "adgroup");
    }

    @SuppressWarnings("unchecked")
    private void collectDate(KakaoMoApiClient api, String advkey, String date,
                              List<Map<String, Object>> campaigns,
                              List<Map<String, Object>> adgroups) {
        if (date.compareTo(LocalDate.now().format(FMT)) >= 0) return;

        String apiDate = LocalDate.parse(date, FMT).format(APIFMT);

        // gid → cid 역매핑
        Map<String, String> gidToCid = new HashMap<>();
        for (Map<String, Object> ag : adgroups) {
            String gid = str(ag, "gid");
            String cid = str(ag, "cid");
            if (gid != null && cid != null) gidToCid.put(gid, cid);
        }

        List<String> adgroupIds = new ArrayList<>(gidToCid.keySet());

        // 20개씩 배치 처리
        for (int i = 0; i < adgroupIds.size(); i += BATCH_SIZE) {
            List<String> batch = adgroupIds.subList(i, Math.min(i + BATCH_SIZE, adgroupIds.size()));
            String batchIds = String.join(",", batch);

            Map<String, String> params = new LinkedHashMap<>();
            params.put("adGroupId",    batchIds);
            params.put("metricsGroup", "BASIC,ADDITION,MESSAGE,PIXEL_SDK_CONVERSION");
            params.put("start",        apiDate);
            params.put("end",          apiDate);
            params.put("timeUnit",     "DAY");
            params.put("level",        "AD_GROUP");
            params.put("dimension",    "CREATIVE_FORMAT");

            Map<String, Object> res = api.get("/openapi/v4/adGroups/report", params);
            if (res == null) { sleep(10_000); continue; }

            Object dataObj = res.get("data");
            if (!(dataObj instanceof List)) { sleep(10_000); continue; }

            // ad_group_id 기준 집계
            Map<String, long[]> agg = new LinkedHashMap<>();
            String rowDate = date;

            for (Map<String, Object> row : (List<Map<String, Object>>) dataObj) {
                Map<String, Object> dims    = (Map<String, Object>) row.get("dimensions");
                Map<String, Object> metrics = (Map<String, Object>) row.get("metrics");
                if (dims == null || metrics == null) continue;

                String adgroupId = str(dims, "ad_group_id");
                if (adgroupId == null) continue;

                String rd = str(row, "start");
                if (rd != null) rowDate = rd;

                long[] sums = agg.computeIfAbsent(adgroupId, k -> new long[5]);
                sums[0] += longVal(metrics, "imp");
                sums[1] += longVal(metrics, "click");
                sums[2] += longVal(metrics, "cost");
                sums[3] += longVal(metrics, "conv_purchase_1d") + longVal(metrics, "conv_signup_1d");
                sums[4] += longVal(metrics, "conv_purchase_p_1d");
            }

            for (Map.Entry<String, long[]> entry : agg.entrySet()) {
                String adgroupId = entry.getKey();
                long[] s = entry.getValue();
                String campaignId = gidToCid.get(adgroupId);

                if (s[0] == 0 && s[1] == 0 && s[2] == 0 && s[3] == 0 && s[4] == 0) continue;
                if (statMongo.existsAdGroupDaily(advkey, rowDate, adgroupId)) continue;

                Map<String, Object> doc = new HashMap<>();
                doc.put("advkey",     advkey);
                doc.put("daily_dt",   rowDate);
                doc.put("campaign_id", campaignId);
                doc.put("adgroup_id", adgroupId);
                doc.put("daily_im",   s[0]);
                doc.put("daily_clk",  s[1]);
                doc.put("daily_cst",  s[2]);
                doc.put("daily_cv",   s[3]);
                doc.put("daily_cr",   s[4]);
                statMongo.insertAdGroupDaily(doc);
            }

            // OFF 그룹 0으로 채우기
            for (String gid : batch) {
                Map<String, Object> ag = adgroups.stream()
                    .filter(a -> gid.equals(str(a, "gid"))).findFirst().orElse(null);
                if (ag == null) continue;
                int onoff = ag.get("onoff") instanceof Number ? ((Number) ag.get("onoff")).intValue() : 0;
                if (onoff == 1) continue;
                if (statMongo.existsAdGroupDaily(advkey, date, gid)) continue;

                Map<String, Object> doc = new HashMap<>();
                doc.put("advkey",     advkey);
                doc.put("daily_dt",   date);
                doc.put("campaign_id", gidToCid.get(gid));
                doc.put("adgroup_id", gid);
                doc.put("daily_im",   0L);
                doc.put("daily_clk",  0L);
                doc.put("daily_cst",  0L);
                doc.put("daily_cv",   0L);
                doc.put("daily_cr",   0L);
                statMongo.insertAdGroupDaily(doc);
            }

            sleep(10_000);
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
