package com.h3.h3_java.batch.stat;

import com.h3.h3_java.batch.scheduler.KakaoSaTokenManager;
import com.h3.h3_java.media.kakao.KakaoSaApiClient;
import com.h3.h3_java.media.kakao.dto.KakaoSaAccountDto;
import com.h3.h3_java.media.kakao.mapper.KakaoSaMapper;
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
public class KakaoSaCampaignDayJob {

    private final KakaoSaMapper             mapper;
    private final KakaoSaMasterMongoService masterMongo;
    private final KakaoSaStatMongoService   statMongo;
    private final KakaoSaTokenManager       tokenManager;

    private static final DateTimeFormatter FMT   = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter APIFMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    // ── 자동 날짜 수집 (D-1, D-3, D-5 + 7일 gap 체크) ─────────────────────────

    public void collect() {
        List<KakaoSaAccountDto> accounts = mapper.selectKakaoSaAccounts();
        log.info("[KAKAO-SA][CAMPAIGN-DAY] 전체 수집 시작 accounts={}", accounts.size());
        List<String> dates = buildAutoDates();
        for (KakaoSaAccountDto account : accounts) {
            collectForAccount(account, dates);
        }
    }

    // ── 단일 유저 자동 날짜 ───────────────────────────────────────────────────

    public boolean collectForUserId(String userId) {
        return collectForUserId(userId, buildAutoDates());
    }

    private boolean collectForUserId(String userId, List<String> dates) {
        KakaoSaAccountDto account = mapper.selectKakaoSaAccounts().stream()
            .filter(a -> userId.equals(a.getUserId()))
            .findFirst().orElse(null);
        if (account == null) return false;
        collectForAccount(account, dates);
        return true;
    }

    // ── 기간 수집 ─────────────────────────────────────────────────────────────

    public boolean collectRange(String userId, String fromDate, String toDate) {
        KakaoSaAccountDto account = mapper.selectKakaoSaAccounts().stream()
            .filter(a -> userId.equals(a.getUserId()))
            .findFirst().orElse(null);
        if (account == null) return false;
        collectForAccount(account, buildDateRange(fromDate, toDate));
        return true;
    }

    // ── 계정별 수집 ───────────────────────────────────────────────────────────

    private void collectForAccount(KakaoSaAccountDto account, List<String> dates) {
        String advkey = account.getAccountKakaosa();
        String token  = tokenManager.getAccessToken();
        if (token == null) {
            log.warn("[KAKAO-SA][CAMPAIGN-DAY][SKIP] 토큰 없음 advkey={}", advkey);
            return;
        }

        KakaoSaApiClient api = new KakaoSaApiClient(token, advkey);

        // 마스터에서 캠페인 목록 로드
        List<Map<String, Object>> campaigns = masterMongo.findCampaigns(advkey);

        for (String date : dates) {
            collectDate(api, advkey, date, campaigns);
        }

        log.info("[KAKAO-SA][CAMPAIGN-DAY] 완료 advkey={} dates={}", advkey, dates.size());
    }

    @SuppressWarnings("unchecked")
    private void collectDate(KakaoSaApiClient api, String advkey, String date,
                              List<Map<String, Object>> campaigns) {
        // 오늘 이후는 skip
        if (date.compareTo(LocalDate.now().format(FMT)) >= 0) return;

        String apiDate = LocalDate.parse(date, FMT).format(APIFMT);

        Map<String, String> params = new LinkedHashMap<>();
        params.put("metricsGroups", "BASIC,ADDITION,PIXEL_SDK_CONVERSION");
        params.put("start", apiDate);
        params.put("end",   apiDate);
        params.put("timeUnit", "DAY");

        Map<String, Object> res = api.get("/openapi/v1/campaigns/report", params);
        if (res == null) return;

        Object dataObj = res.get("data");
        if (!(dataObj instanceof List)) return;
        List<Map<String, Object>> dataList = (List<Map<String, Object>>) dataObj;

        // API 응답 처리
        for (Map<String, Object> row : dataList) {
            Map<String, Object> dims    = (Map<String, Object>) row.get("dimensions");
            Map<String, Object> metrics = (Map<String, Object>) row.get("metrics");
            if (dims == null || metrics == null) continue;

            String campaignId = str(dims, "campaignId");
            String rowDate    = str(row, "start");
            if (campaignId == null) continue;

            long clk = longVal(metrics, "click");
            long im  = longVal(metrics, "imp");
            long cst = longVal(metrics, "spending");
            long cv  = longVal(metrics, "convPurchase1d") + longVal(metrics, "convSignup1d");
            long cr  = longVal(metrics, "convPurchaseP1d");

            if (clk == 0 && im == 0 && cst == 0 && cv == 0 && cr == 0) continue;
            if (statMongo.existsCampaignDaily(advkey, rowDate != null ? rowDate : date, campaignId)) continue;

            Map<String, Object> doc = new HashMap<>();
            doc.put("advkey",      advkey);
            doc.put("daily_dt",    rowDate != null ? rowDate : date);
            doc.put("campaign_id", campaignId);
            doc.put("daily_im",    im);
            doc.put("daily_clk",   clk);
            doc.put("daily_cst",   cst);
            doc.put("daily_cv",    cv);
            doc.put("daily_cr",    cr);
            statMongo.insertCampaignDaily(doc);
        }

        // OFF 캠페인도 0으로 채우기 (아직 해당 날짜 데이터 없는 경우만)
        for (Map<String, Object> campaign : campaigns) {
            int onoff = campaign.get("onoff") instanceof Number ? ((Number) campaign.get("onoff")).intValue() : 0;
            if (onoff == 1) continue; // ON 캠페인은 이미 위에서 처리됨
            String cid = str(campaign, "cid");
            if (cid == null) continue;
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
    }

    // ── 날짜 생성 ─────────────────────────────────────────────────────────────

    private List<String> buildAutoDates() {
        Set<String> dates = new LinkedHashSet<>();
        LocalDate today = LocalDate.now();
        for (int d : new int[]{1, 3, 5}) {
            dates.add(today.minusDays(d).format(FMT));
        }
        // 최근 7일 gap 체크 생략 (캠페인 일별은 단순 D-1/D-3/D-5)
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
