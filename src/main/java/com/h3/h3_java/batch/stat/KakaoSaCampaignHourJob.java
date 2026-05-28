package com.h3.h3_java.batch.stat;

import com.h3.h3_java.batch.scheduler.KakaoSaTokenManager;
import com.h3.h3_java.media.kakao.KakaoSaApiClient;
import com.h3.h3_java.media.kakao.dto.KakaoSaAccountDto;
import com.h3.h3_java.media.kakao.mapper.KakaoSaMapper;
import com.h3.h3_java.raw.mongo.KakaoSaStatMongoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 캠페인 시간별 수집.
 * PHP `h3_salse_hour_kakaokeyword` → MongoDB `kakao_sa_campaign_hour`.
 * 시간별 데이터를 hours 배열([{hour:0, im:, clk:, cst:, cv:, cr:}, ...])로 저장.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KakaoSaCampaignHourJob {

    private final KakaoSaMapper           mapper;
    private final KakaoSaStatMongoService statMongo;
    private final KakaoSaTokenManager     tokenManager;

    private static final DateTimeFormatter FMT    = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter APIFMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    public void collect() {
        List<KakaoSaAccountDto> accounts = mapper.selectKakaoSaAccounts();
        log.info("[KAKAO-SA][CAMPAIGN-HOUR] 전체 수집 시작 accounts={}", accounts.size());
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
        KakaoSaAccountDto account = mapper.selectKakaoSaAccounts().stream()
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
            log.warn("[KAKAO-SA][CAMPAIGN-HOUR][SKIP] 토큰 없음 advkey={}", advkey);
            return;
        }

        KakaoSaApiClient api = new KakaoSaApiClient(token, advkey);

        for (String date : dates) {
            collectDate(api, advkey, date);
        }

        log.info("[KAKAO-SA][CAMPAIGN-HOUR] 완료 advkey={} dates={}", advkey, dates.size());
    }

    @SuppressWarnings("unchecked")
    private void collectDate(KakaoSaApiClient api, String advkey, String date) {
        if (date.compareTo(LocalDate.now().format(FMT)) >= 0) return;
        if (statMongo.existsCampaignHour(advkey, date)) return;

        String apiDate = LocalDate.parse(date, FMT).format(APIFMT);

        Map<String, String> params = new LinkedHashMap<>();
        params.put("metricsGroups", "BASIC,ADDITION,PIXEL_SDK_CONVERSION");
        params.put("start",     apiDate);
        params.put("end",       apiDate);
        params.put("dimension", "HOUR");
        params.put("timeUnit",  "DAY");

        Map<String, Object> res = api.get("/openapi/v1/campaigns/report", params);
        if (res == null) return;

        Object dataObj = res.get("data");
        if (!(dataObj instanceof List)) return;
        List<Map<String, Object>> dataList = (List<Map<String, Object>>) dataObj;

        // 시간별 집계 (24시간 합산)
        long[] im  = new long[24];
        long[] clk = new long[24];
        long[] cst = new long[24];
        long[] cv  = new long[24];
        long[] cr  = new long[24];

        for (Map<String, Object> row : dataList) {
            Map<String, Object> dims    = (Map<String, Object>) row.get("dimensions");
            Map<String, Object> metrics = (Map<String, Object>) row.get("metrics");
            if (dims == null || metrics == null) continue;

            Object hourObj = dims.get("hour");
            if (hourObj == null) continue;
            int hour = ((Number) hourObj).intValue();
            if (hour < 0 || hour > 23) continue;

            im[hour]  += longVal(metrics, "imp");
            clk[hour] += longVal(metrics, "click");
            cst[hour] += longVal(metrics, "spending");
            cv[hour]  += longVal(metrics, "convPurchase1d") + longVal(metrics, "convSignup1d");
            cr[hour]  += longVal(metrics, "convPurchaseP1d");
        }

        // hours 배열 구성
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

        log.debug("[KAKAO-SA][CAMPAIGN-HOUR] 저장 advkey={} date={}", advkey, date);
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
}
