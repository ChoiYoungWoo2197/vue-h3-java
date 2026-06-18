package com.h3.h3_java.batch.stat;

import com.h3.h3_java.batch.scheduler.KakaoSaTokenManager;
import com.h3.h3_java.media.kakao.KakaoSaApiClient;
import com.h3.h3_java.media.kakao.dto.KakaoSaAccountDto;
import com.h3.h3_java.media.kakao.mapper.KakaoSaMapper;
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
public class KakaoSaKeywordDayJob {

    private final KakaoSaMapper             mapper;
    private final KakaoSaMasterMongoService masterMongo;
    private final KakaoSaStatMongoService   statMongo;
    private final KakaoSaTokenManager       tokenManager;
    private final AccountLogMongoService    accountLogMongo;

    private static final DateTimeFormatter FMT    = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter APIFMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    public void collect() {
        List<KakaoSaAccountDto> accounts = mapper.selectKakaoSaAccounts();
        log.info("[KAKAO-SA][KEYWORD-DAY] 전체 수집 시작 accounts={}", accounts.size());
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
            log.warn("[KAKAO-SA][KEYWORD-DAY][SKIP] 토큰 없음 advkey={}", advkey);
            return;
        }

        KakaoSaApiClient api = new KakaoSaApiClient(token, advkey);
        List<Map<String, Object>> adgroups = masterMongo.findAdGroups(advkey);

        for (String date : dates) {
            collectDate(api, advkey, date, adgroups);
        }

        log.info("[KAKAO-SA][KEYWORD-DAY] 완료 advkey={} dates={}", advkey, dates.size());
        accountLogMongo.updateField(advkey, "kakaosa", "keyword");
    }

    @SuppressWarnings("unchecked")
    private void collectDate(KakaoSaApiClient api, String advkey, String date,
                              List<Map<String, Object>> adgroups) {
        if (date.compareTo(LocalDate.now().format(FMT)) >= 0) return;

        String apiDate = LocalDate.parse(date, FMT).format(APIFMT);

        // 1단계: 광고그룹별 키워드 목록 수집
        Map<String, Map<String, Object>> keywordMap = new LinkedHashMap<>(); // kid → doc

        for (Map<String, Object> ag : adgroups) {
            String gid = str(ag, "gid");
            String cid = str(ag, "cid");
            if (gid == null) continue;

            List<Map<String, Object>> keywords = api.getList(
                "/openapi/v1/keywords",
                Map.of("adGroupId", gid)
            );
            if (keywords == null) continue;

            for (Map<String, Object> kw : keywords) {
                String kid   = str(kw, "id");
                String kname = str(kw, "text");
                String onoff = str(kw, "config");
                if (kid == null) continue;

                Map<String, Object> doc = new HashMap<>();
                doc.put("advkey",      advkey);
                doc.put("daily_dt",    date);
                doc.put("campaign_id", cid);
                doc.put("adgroup_id",  gid);
                doc.put("keyword_id",  kid);
                doc.put("kname",       kname);
                doc.put("onoff",       "ON".equals(onoff) ? 1 : 0);
                doc.put("daily_im",    0L);
                doc.put("daily_clk",   0L);
                doc.put("daily_cst",   0L);
                doc.put("daily_cv",    0L);
                doc.put("daily_cr",    0L);
                keywordMap.put(kid, doc);
            }
        }

        // 2단계: 캠페인 단위로 dedup 후 보고서 1회 호출
        Set<String> calledCids = new HashSet<>();
        for (Map<String, Object> ag : adgroups) {
            String cid = str(ag, "cid");
            if (cid == null || !calledCids.add(cid)) continue;

            sleep(3_000);

            Map<String, String> params = new LinkedHashMap<>();
            params.put("campaignId",    cid);
            params.put("metricsGroups", "BASIC,ADDITION,PIXEL_SDK_CONVERSION");
            params.put("start",         apiDate);
            params.put("end",           apiDate);

            Map<String, Object> res = api.get("/openapi/v1/keywords/report", params);
            if (res == null) continue;

            Object dataObj = res.get("data");
            if (!(dataObj instanceof List)) continue;

            for (Map<String, Object> row : (List<Map<String, Object>>) dataObj) {
                Map<String, Object> dims    = (Map<String, Object>) row.get("dimensions");
                Map<String, Object> metrics = (Map<String, Object>) row.get("metrics");
                if (dims == null || metrics == null) continue;

                String kid = str(dims, "keywordId");
                if (kid == null || !keywordMap.containsKey(kid)) continue;

                long clk = longVal(metrics, "click");
                long im  = longVal(metrics, "imp");
                long cst = longVal(metrics, "spending");
                long cv  = longVal(metrics, "convPurchase1d") + longVal(metrics, "convSignup1d");
                long cr  = longVal(metrics, "convPurchaseP1d");

                Map<String, Object> doc = keywordMap.get(kid);
                doc.put("daily_im",  im);
                doc.put("daily_clk", clk);
                doc.put("daily_cst", cst);
                doc.put("daily_cv",  cv);
                doc.put("daily_cr",  cr);
            }
        }

        // DB 저장 (0값 및 OFF 포함하여 저장 — OFF 키워드 중 0값만 제외)
        int saved = 0;
        for (Map<String, Object> doc : keywordMap.values()) {
            long clk = longVal(doc, "daily_clk");
            long im  = longVal(doc, "daily_im");
            long cst = longVal(doc, "daily_cst");
            long cv  = longVal(doc, "daily_cv");
            long cr  = longVal(doc, "daily_cr");

            // 0값이면 skip (PHP 동일 로직)
            if (clk == 0 && im == 0 && cst == 0 && cv == 0 && cr == 0) continue;

            String kid = str(doc, "keyword_id");
            if (statMongo.existsKeywordDaily(advkey, date, kid)) continue;

            statMongo.insertKeywordDaily(doc);
            saved++;
        }

        log.debug("[KAKAO-SA][KEYWORD-DAY] 저장 advkey={} date={} saved={}", advkey, date, saved);
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
