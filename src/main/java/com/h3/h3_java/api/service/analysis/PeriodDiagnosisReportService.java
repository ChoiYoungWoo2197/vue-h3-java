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

/**
 * 기간 단위(주차/월별/요일별) 성과 진단 리포트.
 * trend / insights / groups 세 섹션을 반환하며,
 * 프론트가 UI 전환·렌더링만 담당하도록 집계·랭킹·진단 메시지를 백엔드에서 처리한다.
 */
@Service
@RequiredArgsConstructor
public class PeriodDiagnosisReportService {

    private final AccountMongoService  accountMongo;
    private final DashboardMongoService mongoService;

    private static final DateTimeFormatter FMT  = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter MFMT = DateTimeFormatter.ofPattern("MM.dd");
    private static final String[] DAYWEEK_ORDER = {"sun","mon","tue","wed","thu","fri","sat"};
    private static final Map<String,String> DAYWEEK_LABEL = Map.of(
        "sun","일요일","mon","월요일","tue","화요일","wed","수요일",
        "thu","목요일","fri","금요일","sat","토요일"
    );

    record MediaCfg(String campCol, String advField) {}

    private static final Map<String, MediaCfg> MEDIA_MAP = Map.of(
        "naver",   new MediaCfg("naver_campaign_daily",     "daily_advid"),
        "naverda", new MediaCfg("naver_gfa_campaign_daily", "daily_advid"),
        "kakaosa", new MediaCfg("kakao_sa_campaign_daily",  "advkey"),
        "kakaomo", new MediaCfg("kakao_mo_campaign_daily",  "advkey"),
        "google",  new MediaCfg("google_campaign_daily",    "daily_advid")
    );

    // ─── 진입점 ────────────────────────────────────────────────────────────────

    public Map<String, Object> getPeriodDiagnosisReport(
            String userId, String fromdate, String todate,
            String comparefromdate, String comparetodate,
            String media, String periodUnit) {

        AccountDto acc = accountMongo.findAccountDtoByUserId(userId);
        if (acc == null) return fail("1009", "계정 없음");

        String m = (media != null && !media.isBlank()) ? media : "naver";
        MediaCfg cfg = MEDIA_MAP.get(m);
        if (cfg == null) return fail("1001", "지원하지 않는 매체입니다.");

        String advid = getAdvid(acc, m);
        if (advid == null || advid.isBlank()) return noData(periodUnit);

        boolean hasCompare = comparefromdate != null && !comparefromdate.isBlank()
                          && comparetodate   != null && !comparetodate.isBlank();
        String cfrom = hasCompare ? comparefromdate : null;
        String cto   = hasCompare ? comparetodate   : null;

        // 일별 원본 데이터 조회
        Map<String, Map<String, Object>> curDaily  = mongoService.aggregateByDate(advid, fromdate, todate, cfg.campCol());
        Map<String, Map<String, Object>> cmpDaily  = hasCompare
            ? mongoService.aggregateByDate(advid, cfrom, cto, cfg.campCol())
            : Collections.emptyMap();

        String pu    = (periodUnit != null) ? periodUnit : "week";
        String label;
        String msg;
        List<Map<String, Object>> trend;

        switch (pu) {
            case "month":
                trend = buildMonthlyTrend(curDaily, cmpDaily, hasCompare);
                label = "월별";
                msg   = "선택한 기간을 월별로 나누어 성과 흐름을 분석했습니다.";
                break;
            case "weekday":
                trend = buildWeekdayTrend(curDaily, cmpDaily, hasCompare);
                label = "요일별";
                msg   = "선택한 기간을 요일별로 나누어 성과 흐름을 분석했습니다.";
                break;
            default: // week
                trend = buildWeeklyTrend(curDaily, cmpDaily, fromdate, todate, cfrom, cto, hasCompare);
                label = "주차별";
                msg   = "선택한 기간을 주차별로 나누어 성과 흐름을 분석했습니다.";
        }

        Map<String, List<Map<String, Object>>> insights = buildInsights(trend);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("period_unit",  pu);
        data.put("period_label", label);
        data.put("message",      msg);
        data.put("trend",        trend);
        data.put("insights",     insights);
        data.put("groups",       new ArrayList<>());

        return Map.of("result", "success", "status", "200", "data", data);
    }

    // ─── 주차별 trend ────────────────────────────────────────────────────────

    private List<Map<String, Object>> buildWeeklyTrend(
            Map<String, Map<String, Object>> curDaily,
            Map<String, Map<String, Object>> cmpDaily,
            String fromdate, String todate,
            String cfrom, String cto,
            boolean hasCompare) {

        List<double[]> curBuckets = weekBuckets(curDaily, fromdate, todate);
        List<String>   curRanges  = weekRanges(fromdate, todate);

        List<double[]> cmpBuckets = hasCompare
            ? weekBuckets(cmpDaily, cfrom, cto)
            : Collections.emptyList();

        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < curBuckets.size(); i++) {
            double[] cur = curBuckets.get(i);
            double[] cmp = (i < cmpBuckets.size()) ? cmpBuckets.get(i) : new double[5];

            Map<String, Object> curM = toMetrics(cur);
            Map<String, Object> cmpM = toMetrics(cmp);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("key",        "week_" + (i + 1));
            row.put("label",      (i + 1) + "주차");
            row.put("range_text", curRanges.get(i));
            row.put("current",    curM);
            row.put("compare",    cmpM);
            row.put("diff",       buildDiff(curM, cmpM));
            row.put("per",        buildPer(curM, cmpM));
            result.add(row);
        }
        return result;
    }

    /** fromdate~todate 구간을 7일씩 나눠 각 버킷의 im/clk/cst/cv/cr 합계 반환 */
    private List<double[]> weekBuckets(Map<String, Map<String, Object>> daily, String from, String to) {
        LocalDate start = LocalDate.parse(from, FMT);
        LocalDate end   = LocalDate.parse(to,   FMT);
        List<double[]> buckets = new ArrayList<>();
        LocalDate ws = start;
        while (!ws.isAfter(end)) {
            LocalDate we = ws.plusDays(6);
            if (we.isAfter(end)) we = end;
            double[] sums = new double[5];
            for (LocalDate d = ws; !d.isAfter(we); d = d.plusDays(1)) {
                Map<String, Object> row = daily.getOrDefault(d.format(FMT), Collections.emptyMap());
                sums[0] += num(row, "im");
                sums[1] += num(row, "clk");
                sums[2] += num(row, "cst");
                sums[3] += num(row, "cv");
                sums[4] += num(row, "cr");
            }
            buckets.add(sums);
            ws = we.plusDays(1);
        }
        return buckets;
    }

    /** fromdate~todate 구간 주차별 range_text 목록 */
    private List<String> weekRanges(String from, String to) {
        LocalDate start = LocalDate.parse(from, FMT);
        LocalDate end   = LocalDate.parse(to,   FMT);
        List<String> ranges = new ArrayList<>();
        LocalDate ws = start;
        while (!ws.isAfter(end)) {
            LocalDate we = ws.plusDays(6);
            if (we.isAfter(end)) we = end;
            ranges.add(ws.format(MFMT) + " ~ " + we.format(MFMT));
            ws = we.plusDays(1);
        }
        return ranges;
    }

    // ─── 월별 trend ──────────────────────────────────────────────────────────

    private List<Map<String, Object>> buildMonthlyTrend(
            Map<String, Map<String, Object>> curDaily,
            Map<String, Map<String, Object>> cmpDaily,
            boolean hasCompare) {

        Map<String, double[]> curMonth = groupByMonth(curDaily);
        Map<String, double[]> cmpMonth = hasCompare ? groupByMonth(cmpDaily) : Collections.emptyMap();

        List<String> months = new ArrayList<>(curMonth.keySet());
        Collections.sort(months);

        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < months.size(); i++) {
            String key = months.get(i);
            double[] cur = curMonth.get(key);
            double[] cmp = cmpMonth.getOrDefault(key, new double[5]);

            Map<String, Object> curM = toMetrics(cur);
            Map<String, Object> cmpM = toMetrics(cmp);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("key",        "month_" + (i + 1));
            row.put("label",      key);
            row.put("range_text", key);
            row.put("current",    curM);
            row.put("compare",    cmpM);
            row.put("diff",       buildDiff(curM, cmpM));
            row.put("per",        buildPer(curM, cmpM));
            result.add(row);
        }
        return result;
    }

    private Map<String, double[]> groupByMonth(Map<String, Map<String, Object>> daily) {
        Map<String, double[]> result = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Object>> e : daily.entrySet()) {
            String month = e.getKey().substring(0, 7);
            result.merge(month, toArr(e.getValue()), this::mergeArr);
        }
        return result;
    }

    // ─── 요일별 trend ────────────────────────────────────────────────────────

    private List<Map<String, Object>> buildWeekdayTrend(
            Map<String, Map<String, Object>> curDaily,
            Map<String, Map<String, Object>> cmpDaily,
            boolean hasCompare) {

        Map<String, double[]> curDay = groupByDayweek(curDaily);
        Map<String, double[]> cmpDay = hasCompare ? groupByDayweek(cmpDaily) : Collections.emptyMap();

        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < DAYWEEK_ORDER.length; i++) {
            String dk  = DAYWEEK_ORDER[i];
            double[] cur = curDay.getOrDefault(dk, new double[5]);
            double[] cmp = cmpDay.getOrDefault(dk, new double[5]);

            Map<String, Object> curM = toMetrics(cur);
            Map<String, Object> cmpM = toMetrics(cmp);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("key",        "dayweek_" + dk);
            row.put("label",      DAYWEEK_LABEL.getOrDefault(dk, dk));
            row.put("range_text", DAYWEEK_LABEL.getOrDefault(dk, dk));
            row.put("current",    curM);
            row.put("compare",    cmpM);
            row.put("diff",       buildDiff(curM, cmpM));
            row.put("per",        buildPer(curM, cmpM));
            result.add(row);
        }
        return result;
    }

    private Map<String, double[]> groupByDayweek(Map<String, Map<String, Object>> daily) {
        Map<String, double[]> result = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Object>> e : daily.entrySet()) {
            try {
                DayOfWeek dow = LocalDate.parse(e.getKey(), FMT).getDayOfWeek();
                String dk = dow.toString().substring(0, 3).toLowerCase();
                result.merge(dk, toArr(e.getValue()), this::mergeArr);
            } catch (Exception ignored) {}
        }
        return result;
    }

    // ─── insights ────────────────────────────────────────────────────────────

    private Map<String, List<Map<String, Object>>> buildInsights(List<Map<String, Object>> trend) {
        Map<String, List<Map<String, Object>>> ins = new LinkedHashMap<>();
        ins.put("main",       buildMainInsights(trend));
        ins.put("cost",       buildCostInsights(trend));
        ins.put("conversion", buildConversionInsights(trend));
        ins.put("roas",       buildRoasInsights(trend));
        return ins;
    }

    private List<Map<String, Object>> buildMainInsights(List<Map<String, Object>> trend) {
        return trend.stream()
            .sorted((a, b) -> Double.compare(
                Math.abs(dbl((Map<?,?>) b.get("diff"), "cst")),
                Math.abs(dbl((Map<?,?>) a.get("diff"), "cst"))))
            .limit(3)
            .map(w -> toInsightItem(w, "main"))
            .collect(Collectors.toList());
    }

    private List<Map<String, Object>> buildCostInsights(List<Map<String, Object>> trend) {
        return trend.stream()
            .sorted((a, b) -> Double.compare(
                dbl((Map<?,?>) b.get("current"), "cst"),
                dbl((Map<?,?>) a.get("current"), "cst")))
            .limit(3)
            .map(w -> toInsightItem(w, "cost"))
            .collect(Collectors.toList());
    }

    private List<Map<String, Object>> buildConversionInsights(List<Map<String, Object>> trend) {
        return trend.stream()
            .filter(w -> dbl((Map<?,?>) w.get("current"), "cst") > 0)
            .sorted((a, b) -> Double.compare(
                dbl((Map<?,?>) a.get("current"), "cv"),
                dbl((Map<?,?>) b.get("current"), "cv")))
            .limit(3)
            .map(w -> toInsightItem(w, "conversion"))
            .collect(Collectors.toList());
    }

    private List<Map<String, Object>> buildRoasInsights(List<Map<String, Object>> trend) {
        return trend.stream()
            .filter(w -> dbl((Map<?,?>) w.get("current"), "cst") > 0)
            .sorted((a, b) -> Double.compare(
                dbl((Map<?,?>) a.get("current"), "purchase_roas"),
                dbl((Map<?,?>) b.get("current"), "purchase_roas")))
            .limit(3)
            .map(w -> toInsightItem(w, "roas"))
            .collect(Collectors.toList());
    }

    private Map<String, Object> toInsightItem(Map<String, Object> week, String type) {
        String label     = (String) week.get("label");
        String rangeText = (String) week.get("range_text");
        String key       = (String) week.get("key");
        Map<?,?> cur     = (Map<?,?>) week.get("current");
        Map<?,?> diff    = (Map<?,?>) week.get("diff");

        String reason;
        String valueText;
        String tone = "info";

        switch (type) {
            case "cost": {
                double cst = dbl(cur, "cst");
                reason    = "광고비 " + formatPrice(cst) + "원 집행";
                valueText = formatPrice(cst) + "원";
                break;
            }
            case "conversion": {
                double cv  = dbl(cur, "cv");
                double cvr = dbl(cur, "cvr");
                reason    = "전환수 " + round2(cv) + "건 · CVR " + round2(cvr) + "%";
                valueText = round2(cv) + "건";
                if (cv < 1) tone = "warning";
                break;
            }
            case "roas": {
                double pr = dbl(cur, "purchase_roas");
                reason    = "구매완료수익률 " + round2(pr) + "%";
                valueText = round2(pr) + "%";
                if (pr < 100) tone = "warning";
                break;
            }
            default: { // main / impact
                double dCst = dbl(diff, "cst");
                double dCv  = dbl(diff, "cv");
                reason    = "광고비 " + (dCst >= 0 ? "+" : "") + formatPrice(dCst) + "원";
                if (dCv != 0) reason += " · 전환 " + (dCv >= 0 ? "+" : "") + round2(dCv) + "건";
                valueText = formatPrice(dbl(cur, "cst")) + "원";
                if (dCst < 0 || dCv < 0) tone = "warning";
            }
        }

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id",          key);
        item.put("name",        label);
        item.put("campaign_id", null);
        item.put("range_text",  rangeText);
        item.put("reason",      reason);
        item.put("value_text",  valueText);
        item.put("tone",        tone);
        item.put("current",     cur);
        item.put("compare",     week.get("compare"));
        item.put("diff",        week.get("diff"));
        item.put("per",         week.get("per"));
        return item;
    }

    // ─── metrics 빌더 ─────────────────────────────────────────────────────────

    private Map<String, Object> toMetrics(double[] v) {
        double im = v[0], clk = v[1], cst = v[2], cv = v[3], cr = v[4];
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("im",  (long) im);
        m.put("clk", (long) clk);
        m.put("cst", (long) cst);
        m.put("cv",  round2(cv));
        m.put("cr",  round2(cr));
        m.put("ctr",  (im  > 0 && clk > 0) ? round2(clk / im  * 100) : 0);
        m.put("cpc",  (cst > 0 && clk > 0) ? round2(cst / clk)       : 0);
        m.put("cpa",  (cst > 0 && cv  > 0) ? round2(cst / cv)        : 0);
        m.put("cvr",  (cv  > 0 && clk > 0) ? round2(cv  / clk * 100) : 0);
        m.put("roas", (cr  > 0 && cst > 0) ? round2(cr  / cst * 100) : 0);
        m.put("purchase_roas", (cr > 0 && cst > 0) ? round2(cr / cst * 100) : 0);
        return m;
    }

    private Map<String, Object> buildDiff(Map<String, Object> cur, Map<String, Object> cmp) {
        Map<String, Object> d = new LinkedHashMap<>();
        for (String k : new String[]{"im","clk","cst","cv","cr","ctr","cpc","cpa","cvr","roas","purchase_roas"}) {
            d.put(k, round2(dbl(cur, k) - dbl(cmp, k)));
        }
        return d;
    }

    private Map<String, Object> buildPer(Map<String, Object> cur, Map<String, Object> cmp) {
        Map<String, Object> p = new LinkedHashMap<>();
        for (String k : new String[]{"im","clk","cst","cv","cr","ctr","cpc","cpa","cvr","roas","purchase_roas"}) {
            double a = dbl(cur, k), b = dbl(cmp, k);
            p.put(k, b > 0 ? round2((a - b) / b * 100) : 0);
        }
        return p;
    }

    // ─── 헬퍼 ────────────────────────────────────────────────────────────────

    private double[] toArr(Map<String, Object> row) {
        return new double[]{ num(row,"im"), num(row,"clk"), num(row,"cst"), num(row,"cv"), num(row,"cr") };
    }

    private double[] mergeArr(double[] a, double[] b) {
        for (int i = 0; i < a.length; i++) a[i] += b[i];
        return a;
    }

    private double num(Map<String, Object> m, String key) {
        Object v = m == null ? null : m.get(key);
        return v instanceof Number ? ((Number) v).doubleValue() : 0.0;
    }

    private double dbl(Map<?,?> m, String key) {
        if (m == null) return 0;
        Object v = m.get(key);
        return v instanceof Number ? ((Number) v).doubleValue() : 0;
    }

    private double round2(double v) { return Math.round(v * 100.0) / 100.0; }

    private String formatPrice(double v) {
        return String.format("%,d", (long) Math.round(v));
    }

    private String getAdvid(AccountDto acc, String media) {
        return switch (media) {
            case "naver"   -> acc.getAccountNaverCustomer();
            case "naverda" -> acc.getAccountGfa();
            case "kakaosa" -> acc.getAccountKakaosa();
            case "kakaomo" -> acc.getAccountKakaomoment();
            case "google"  -> acc.getAccountGoogle();
            default -> null;
        };
    }

    private Map<String, Object> fail(String status, String msg) {
        return Map.of("result", "failed", "status", status, "errormessage", msg);
    }

    private Map<String, Object> noData(String periodUnit) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("period_unit",  periodUnit != null ? periodUnit : "week");
        data.put("period_label", "주차별");
        data.put("message",      "해당 매체 계정이 없습니다.");
        data.put("trend",        new ArrayList<>());
        data.put("insights",     Map.of("main", List.of(), "cost", List.of(), "conversion", List.of(), "roas", List.of()));
        data.put("groups",       new ArrayList<>());
        return Map.of("result", "success", "status", "200", "data", data);
    }
}
