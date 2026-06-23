package com.h3.h3_java.api.service.analysis;

import com.h3.h3_java.api.dto.AccountDto;
import com.h3.h3_java.raw.mongo.AccountMongoService;
import com.h3.h3_java.raw.mongo.DashboardMongoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PeriodReportService {

    private final AccountMongoService accountMongo;
    private final DashboardMongoService mongoService;

    // 매체별 컬렉션 정의
    private static final String[] MEDIA_KEYS = {"naver", "kakaosa", "kakaomo", "naverda", "google"};
    private static final String[] DAY_COLLECTIONS = {
        "naver_campaign_daily", "kakao_sa_campaign_daily",
        "kakao_mo_campaign_daily", "naver_gfa_campaign_daily", "google_campaign_daily"
    };
    private static final String[] HOUR_COLLECTIONS = {
        "naver_campaign_hour", "kakao_sa_campaign_hour",
        "kakao_mo_campaign_hour", null /* GFA는 hour 없음 */, "google_campaign_hour"
    };
    private static final String[] DAYWEEK_ORDER = {"sun","mon","tue","wed","thu","fri","sat"};

    public Map<String, Object> getPeriodReport(
            String userId, String fromdate, String todate,
            String comparefromdate, String comparetodate, String periodunit) {

        AccountDto acc = accountMongo.findAccountDtoByUserId(userId);
        if (acc == null) return fail("1009", "계정을 확인해 주세요.");

        String[] advkeys = {
            acc.getAccountNaverCustomer(),
            acc.getAccountKakaosa(),
            acc.getAccountKakaomoment(),
            acc.getAccountGfa(),
            acc.getAccountGoogle()
        };

        boolean hasCompare = comparefromdate != null && !comparefromdate.isBlank()
                          && comparetodate   != null && !comparetodate.isBlank();
        String cfrom = hasCompare ? comparefromdate : fromdate;
        String cto   = hasCompare ? comparetodate   : todate;

        // periodunit별 분기
        Map<String, Map<String, Map<String, Object>>> mediaData;   // media → key → {im,clk,...}
        Map<String, Map<String, Map<String, Object>>> cmpMediaData;

        if ("hour".equals(periodunit)) {
            mediaData    = collectHour(advkeys, fromdate, todate);
            cmpMediaData = collectHour(advkeys, cfrom,    cto);
        } else if ("dayweek".equals(periodunit)) {
            mediaData    = collectDayWeek(advkeys, fromdate, todate);
            cmpMediaData = collectDayWeek(advkeys, cfrom,    cto);
        } else if ("month".equals(periodunit)) {
            mediaData    = collectMonth(advkeys, fromdate, todate);
            cmpMediaData = collectMonth(advkeys, cfrom,    cto);
        } else {
            mediaData    = collectDay(advkeys, fromdate, todate);
            cmpMediaData = collectDay(advkeys, cfrom,    cto);
        }

        if (mediaData.values().stream().allMatch(Map::isEmpty)) {
            return noData();
        }

        // media 섹션 (metrics 추가)
        Map<String, Object> mediaSec = new LinkedHashMap<>();
        for (int i = 0; i < MEDIA_KEYS.length; i++) {
            mediaSec.put(MEDIA_KEYS[i], applyMetrics(mediaData.get(MEDIA_KEYS[i])));
        }

        // total 섹션
        Map<String, Object> total = buildTotal(mediaData, cmpMediaData, hasCompare, advkeys, periodunit);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", total);
        data.put("media", mediaSec);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("result", "success");
        res.put("status", "200");
        res.put("data", data);
        return res;
    }

    // ─── 수집 ────────────────────────────────────────────────────────────────

    /** day 모드: media → date → {im,clk,cst,cv,cr} */
    private Map<String, Map<String, Map<String, Object>>> collectDay(String[] advkeys, String from, String to) {
        Map<String, Map<String, Map<String, Object>>> result = new LinkedHashMap<>();
        for (int i = 0; i < MEDIA_KEYS.length; i++) {
            String advkey = advkeys[i];
            Map<String, Map<String, Object>> byDate = new LinkedHashMap<>();
            if (advkey != null && !advkey.isBlank()) {
                Map<String, Map<String, Object>> raw = mongoService.aggregateByDate(advkey, from, to, DAY_COLLECTIONS[i]);
                raw.forEach((date, row) -> byDate.put(date, new LinkedHashMap<>(row)));
            }
            result.put(MEDIA_KEYS[i], byDate);
        }
        return result;
    }

    /** hour 모드: media → date → hour → {im,clk,cst,cv,cr} (중첩 Map을 평탄화) */
    private Map<String, Map<String, Map<String, Object>>> collectHour(String[] advkeys, String from, String to) {
        Map<String, Map<String, Map<String, Object>>> result = new LinkedHashMap<>();
        for (int i = 0; i < MEDIA_KEYS.length; i++) {
            Map<String, Map<String, Object>> flat = new LinkedHashMap<>();
            if (HOUR_COLLECTIONS[i] != null) {
                String advkey = advkeys[i];
                if (advkey != null && !advkey.isBlank()) {
                    Map<String, Map<String, Map<String, Object>>> hourData =
                        mongoService.aggregateHourByDate(advkey, from, to, HOUR_COLLECTIONS[i]);
                    // 평탄화: "YYYY-MM-DD|HH" → {im,clk,...}
                    hourData.forEach((date, hourMap) ->
                        hourMap.forEach((hh, row) -> flat.put(date + "|" + hh, new LinkedHashMap<>(row))));
                }
            }
            result.put(MEDIA_KEYS[i], flat);
        }
        return result;
    }

    /** dayweek 모드: media → dayName → {im,clk,...} 합산 */
    private Map<String, Map<String, Map<String, Object>>> collectDayWeek(String[] advkeys, String from, String to) {
        Map<String, Map<String, Map<String, Object>>> result = new LinkedHashMap<>();
        for (int i = 0; i < MEDIA_KEYS.length; i++) {
            Map<String, Map<String, Object>> grouped = new LinkedHashMap<>();
            String advkey = advkeys[i];
            if (advkey != null && !advkey.isBlank()) {
                Map<String, Map<String, Object>> byDate = mongoService.aggregateByDate(advkey, from, to, DAY_COLLECTIONS[i]);
                for (Map.Entry<String, Map<String, Object>> e : byDate.entrySet()) {
                    String dayName = toDayName(e.getKey());
                    grouped.merge(dayName, new LinkedHashMap<>(e.getValue()), PeriodReportService::mergeStats);
                }
            }
            result.put(MEDIA_KEYS[i], grouped);
        }
        return result;
    }

    /** month 모드: media → YYYY-MM → {im,clk,...} 합산 */
    private Map<String, Map<String, Map<String, Object>>> collectMonth(String[] advkeys, String from, String to) {
        Map<String, Map<String, Map<String, Object>>> result = new LinkedHashMap<>();
        for (int i = 0; i < MEDIA_KEYS.length; i++) {
            Map<String, Map<String, Object>> grouped = new LinkedHashMap<>();
            String advkey = advkeys[i];
            if (advkey != null && !advkey.isBlank()) {
                Map<String, Map<String, Object>> byDate = mongoService.aggregateByDate(advkey, from, to, DAY_COLLECTIONS[i]);
                for (Map.Entry<String, Map<String, Object>> e : byDate.entrySet()) {
                    String month = e.getKey().substring(0, 7); // YYYY-MM
                    grouped.merge(month, new LinkedHashMap<>(e.getValue()), PeriodReportService::mergeStats);
                }
            }
            result.put(MEDIA_KEYS[i], grouped);
        }
        return result;
    }

    // ─── total 섹션 빌드 ─────────────────────────────────────────────────────

    private Map<String, Object> buildTotal(
            Map<String, Map<String, Map<String, Object>>> mediaData,
            Map<String, Map<String, Map<String, Object>>> cmpData,
            boolean hasCompare,
            String[] advkeys,
            String periodunit) {

        Map<String, Object> total = new LinkedHashMap<>();

        // 전체 키셋 (5개 media 합집합)
        Set<String> allKeys = new LinkedHashSet<>();
        for (String mk : MEDIA_KEYS) allKeys.addAll(mediaData.get(mk).keySet());

        // dayweek 정렬
        if ("dayweek".equals(periodunit)) {
            Set<String> sorted = new LinkedHashSet<>(Arrays.asList(DAYWEEK_ORDER));
            sorted.retainAll(allKeys);
            allKeys = sorted;
        }

        // summary 구축용 누적
        Map<String, double[]> mediaSums    = new LinkedHashMap<>(); // media → [im,clk,cst,cv,cr]
        Map<String, double[]> mediaCmpSums = new LinkedHashMap<>();
        for (String mk : MEDIA_KEYS) {
            mediaSums.put(mk,    new double[5]);
            mediaCmpSums.put(mk, new double[5]);
        }

        // 키별 total 행 (all media 합산)
        for (String key : allKeys) {
            double[] agg = new double[5]; // im,clk,cst,cv,cr
            for (int i = 0; i < MEDIA_KEYS.length; i++) {
                String mk  = MEDIA_KEYS[i];
                Map<String, Object> row = mediaData.get(mk).getOrDefault(key, Collections.emptyMap());
                double[] vals = toDoubleArr(row);
                for (int j = 0; j < 5; j++) { agg[j] += vals[j]; mediaSums.get(mk)[j] += vals[j]; }
            }
            Map<String, Object> keyRow = buildStatRow(agg);
            total.put(key, keyRow);
        }

        // 비교기간 media summary 합산
        for (String mk : MEDIA_KEYS) {
            for (Map<String, Object> row : cmpData.get(mk).values()) {
                double[] v = toDoubleArr(row);
                for (int j = 0; j < 5; j++) mediaCmpSums.get(mk)[j] += v[j];
            }
        }

        // summary 빌드
        Map<String, Object> summary = buildSummary(mediaSums, mediaCmpSums, hasCompare);
        total.put("summary", summary);

        return total;
    }

    private Map<String, Object> buildSummary(
            Map<String, double[]> mediaSums,
            Map<String, double[]> mediaCmpSums,
            boolean hasCompare) {

        // all(합계)
        double[] allSum = new double[5];
        for (double[] v : mediaSums.values()) for (int j = 0; j < 5; j++) allSum[j] += v[j];

        Map<String, Object> all = buildStatRow(allSum);

        // media별 stats + cp
        for (String mk : MEDIA_KEYS) {
            Map<String, Object> mStat = buildStatRow(mediaSums.get(mk));
            if (hasCompare) {
                mStat.put("cp", buildCp2(mediaSums.get(mk), mediaCmpSums.get(mk)));
            }
            all.put(mk, mStat);
        }

        // media별 독립 summary
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("all", all);
        for (String mk : MEDIA_KEYS) {
            summary.put(mk, buildStatRow(mediaSums.get(mk)));
        }
        return summary;
    }

    // ─── 헬퍼 ────────────────────────────────────────────────────────────────

    /** {date/hour/dayweek → row} 전체에 metrics(ctr/cpc/cpa/cvr/roas) 추가 */
    private Map<String, Object> applyMetrics(Map<String, Map<String, Object>> byKey) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Object>> e : byKey.entrySet()) {
            double[] v = toDoubleArr(e.getValue());
            result.put(e.getKey(), buildStatRow(v));
        }
        return result;
    }

    private Map<String, Object> buildStatRow(double[] v) {
        double im = v[0], clk = v[1], cst = v[2], cv = v[3], cr = v[4];
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("im",  (long) im);  r.put("clk", (long) clk); r.put("cst", (long) cst);
        r.put("cv",  round2(cv)); r.put("cr",  round2(cr));
        r.put("ctr",  (im>0&&clk>0)  ? round2(clk/im*100)  : 0);
        r.put("cpc",  (cst>0&&clk>0) ? round2(cst/clk)     : 0);
        r.put("cpa",  (cst>0&&cv>0)  ? round2(cst/cv)      : 0);
        r.put("cvr",  (cv>0&&clk>0)  ? round2(cv/clk*100)  : 0);
        r.put("roas", (cr>0&&cst>0)  ? round2(cr/cst*100)  : 0);
        return r;
    }

    private Map<String, Object> buildCp2(double[] cur, double[] cmp) {
        String[] kpis = {"im","clk","cst","cv","cr","ctr","cpc","cpa","cvr","roas"};
        // 계산용 통계 값 배열 구성 (cur/cmp의 파생지표 포함)
        double[] curMetrics = statsArr(cur);
        double[] cmpMetrics = statsArr(cmp);

        Map<String, Object> per = new LinkedHashMap<>();
        Map<String, Object> cpVal = new LinkedHashMap<>();
        for (int i = 0; i < kpis.length; i++) {
            double a = curMetrics[i], b = cmpMetrics[i];
            if (a > 0 && b > 0) {
                per.put(kpis[i],  round1((a - b) / b * 100));
                cpVal.put(kpis[i], b);
            } else {
                per.put(kpis[i], 0);
                cpVal.put(kpis[i], 0);
            }
        }
        Map<String, Object> cp = new LinkedHashMap<>();
        cp.put("per", per); cp.put("cp", cpVal);
        return cp;
    }

    /** [im, clk, cst, cv, cr, ctr, cpc, cpa, cvr, roas] */
    private double[] statsArr(double[] v) {
        double im = v[0], clk = v[1], cst = v[2], cv = v[3], cr = v[4];
        return new double[]{
            im, clk, cst, cv, cr,
            (im>0&&clk>0)  ? clk/im*100  : 0,
            (cst>0&&clk>0) ? cst/clk     : 0,
            (cst>0&&cv>0)  ? cst/cv      : 0,
            (cv>0&&clk>0)  ? cv/clk*100  : 0,
            (cr>0&&cst>0)  ? cr/cst*100  : 0
        };
    }

    private double[] toDoubleArr(Map<String, Object> row) {
        return new double[]{
            num(row, "im"), num(row, "clk"), num(row, "cst"), num(row, "cv"), num(row, "cr")
        };
    }

    private static Map<String, Object> mergeStats(Map<String, Object> a, Map<String, Object> b) {
        String[] keys = {"im","clk","cst","cv","cr"};
        for (String k : keys) {
            double va = a.containsKey(k) ? ((Number)a.get(k)).doubleValue() : 0;
            double vb = b.containsKey(k) ? ((Number)b.get(k)).doubleValue() : 0;
            a.put(k, va + vb);
        }
        return a;
    }

    private String toDayName(String dateStr) {
        try {
            LocalDate d = LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE);
            return d.getDayOfWeek().toString().substring(0, 3).toLowerCase(); // mon, tue...
        } catch (Exception e) {
            return dateStr;
        }
    }

    private double num(Map<String, Object> m, String key) {
        Object v = m == null ? null : m.get(key);
        return v instanceof Number ? ((Number) v).doubleValue() : 0.0;
    }
    private double round2(double v) { return Math.round(v * 100.0) / 100.0; }
    private double round1(double v) { return Math.round(v * 10.0)  / 10.0;  }

    private Map<String, Object> fail(String status, String msg) {
        return Map.of("result","failed","status",status,"errormessage",msg);
    }
    private Map<String, Object> noData() {
        return Map.of("result","success","status","1004","errormessage","검색 결과가 없습니다.");
    }
}
