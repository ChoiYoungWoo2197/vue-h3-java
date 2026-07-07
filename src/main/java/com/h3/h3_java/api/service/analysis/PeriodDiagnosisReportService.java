package com.h3.h3_java.api.service.analysis;

import com.h3.h3_java.api.dto.AccountDto;
import com.h3.h3_java.raw.mongo.AccountMongoService;
import com.h3.h3_java.raw.mongo.DashboardMongoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 기간 단위(주차/월별/요일별) 성과 진단 리포트.
 * trend / insights(diff 기준 B안) / groups(campaign_type 별 + period_items 주차별) 반환.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PeriodDiagnosisReportService {

    private final AccountMongoService   accountMongo;
    private final DashboardMongoService mongoService;

    private static final DateTimeFormatter FMT  = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter MFMT = DateTimeFormatter.ofPattern("MM.dd");

    private static final String[] DAYWEEK_ORDER = {"mon","tue","wed","thu","fri","sat","sun"};
    private static final Map<String,String> DAYWEEK_LABEL = Map.of(
        "sun","일요일","mon","월요일","tue","화요일","wed","수요일",
        "thu","목요일","fri","금요일","sat","토요일"
    );

    record MediaCfg(String campCol, String advField, String campMasterCol, String convtypeCol) {}

    private static final Map<String, MediaCfg> MEDIA_MAP = Map.of(
        "naver",   new MediaCfg("naver_campaign_daily",     "daily_advid", "naver_campaign",     "naver_campaign_convtype"),
        "naverda", new MediaCfg("naver_gfa_campaign_daily", "daily_advid", "naver_gfa_campaign", "naver_gfa_campaign_convtype"),
        "kakaosa", new MediaCfg("kakao_sa_campaign_daily",  "advkey",      "kakao_sa_campaign",  null),
        "kakaomo", new MediaCfg("kakao_mo_campaign_daily",  "advkey",      "kakao_mo_campaign",  null),
        "google",  new MediaCfg("google_campaign_daily",    "daily_advid", "google_campaign",    null)
    );

    private static final Map<Integer, String> NAVER_TYPE_CODE = Map.of(
        1, "web_site", 2, "shopping", 3, "power_contents", 4, "brand_search", 6, "place"
    );
    // NaverGfaMasterJob.TYPE_SET의 역매핑 (Integer → String)
    private static final Map<Integer, String> GFA_TYPE_CODE = Map.of(
        0, "conversion", 1, "web_site_traffic", 2, "install_app", 3, "watch_video",
        4, "catalog",    5, "shopping",          6, "lead",         7, "pmax"
    );
    private static final Map<Integer, String> GOOGLE_TYPE_CODE = Map.ofEntries(
        Map.entry(1,  "demand_gen"),     Map.entry(2,  "display"),
        Map.entry(3,  "hotel"),          Map.entry(4,  "local"),
        Map.entry(5,  "local_services"), Map.entry(6,  "multi_channel"),
        Map.entry(7,  "performance_max"),Map.entry(8,  "search"),
        Map.entry(9,  "shopping"),       Map.entry(10, "smart"),
        Map.entry(11, "travel"),         Map.entry(12, "video"),
        Map.entry(13, "unknown")
    );

    private static final Map<String, Map<String, String>> GROUP_NAMES;
    static {
        Map<String, Map<String, String>> m = new LinkedHashMap<>();
        m.put("naver", Map.of(
            "web_site","파워링크","shopping","쇼핑검색","power_contents","파워컨텐츠",
            "brand_search","브랜드검색","place","플레이스"
        ));
        m.put("naverda", Map.of(
            "conversion","웹사이트 전환","web_site_traffic","인지도 및 트래픽",
            "install_app","앱 전환","watch_video","동영상 조회",
            "catalog","카탈로그","shopping","쇼핑","lead","리드","pmax","퍼포먼스맥스"
        ));
        m.put("kakaosa", Map.of("none","캠페인목록"));
        m.put("kakaomo", Map.of(
            "talk_biz_board","카카오톡비즈보드","display","디스플레이",
            "talk_channel","카카오톡 채널","daum_shopping","다음쇼핑",
            "video","비디오","sponsored_board","브랜드보드"
        ));
        Map<String, String> googleNames = new LinkedHashMap<>();
        googleNames.put("demand_gen","디멘드젠 캠페인"); googleNames.put("display","디스플레이");
        googleNames.put("hotel","호텔");           googleNames.put("search","검색 광고");
        googleNames.put("shopping","쇼핑");        googleNames.put("performance_max","퍼포먼스맥스");
        googleNames.put("video","비디오");         googleNames.put("smart","스마트");
        googleNames.put("local","로컬");           googleNames.put("multi_channel","멀티채널");
        googleNames.put("travel","여행");          googleNames.put("local_services","로컬서비스");
        m.put("google", googleNames);
        GROUP_NAMES = Collections.unmodifiableMap(m);
    }

    // ─── 진입점 ──────────────────────────────────────────────────────────

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

        Map<String, Map<String, Object>> curDaily = mongoService.aggregateByDate(advid, fromdate, todate, cfg.campCol());
        Map<String, Map<String, Object>> cmpDaily = hasCompare
            ? mongoService.aggregateByDate(advid, cfrom, cto, cfg.campCol())
            : Collections.emptyMap();

        if (cfg.convtypeCol() != null) {
            Map<String, Map<String, Object>> curCt = mongoService.aggregateConvtypeByDate(advid, fromdate, todate, cfg.convtypeCol());
            mergeConvtypeIntoDaily(curDaily, curCt);
            if (hasCompare) {
                Map<String, Map<String, Object>> cmpCt = mongoService.aggregateConvtypeByDate(advid, cfrom, cto, cfg.convtypeCol());
                mergeConvtypeIntoDaily(cmpDaily, cmpCt);
            }
        }

        String pu = (periodUnit != null) ? periodUnit : "week";
        String label;
        String msg;
        List<Map<String, Object>> trend;

        switch (pu) {
            case "month":
                trend = buildMonthlyTrend(curDaily, cmpDaily, fromdate, todate, cfrom, cto, hasCompare);
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
        List<Map<String, Object>> groups = buildGroups(advid, m, cfg, fromdate, todate, cfrom, cto, hasCompare, pu);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("period_unit",  pu);
        data.put("period_label", label);
        data.put("message",      msg);
        data.put("trend",        trend);
        data.put("insights",     insights);
        data.put("groups",       groups);
        return Map.of("result", "success", "status", "200", "data", data);
    }

    // ─── 주차별 trend ────────────────────────────────────────────────────

    private List<Map<String, Object>> buildWeeklyTrend(
            Map<String, Map<String, Object>> curDaily,
            Map<String, Map<String, Object>> cmpDaily,
            String fromdate, String todate,
            String cfrom, String cto,
            boolean hasCompare) {

        List<double[]> curBuckets = weekBuckets(curDaily, fromdate, todate);
        List<String>   curRanges  = weekRanges(fromdate, todate);
        List<int[]>    curDays    = weekDayCounts(fromdate, todate);

        List<double[]> cmpBuckets = hasCompare ? weekBuckets(cmpDaily, cfrom, cto) : Collections.emptyList();
        List<String>   cmpRanges  = hasCompare ? weekRanges(cfrom, cto) : Collections.emptyList();

        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < curBuckets.size(); i++) {
            double[] cur = curBuckets.get(i);
            double[] cmp = (i < cmpBuckets.size()) ? cmpBuckets.get(i) : new double[15];
            int[]    dc  = curDays.get(i);

            Map<String, Object> curM = toMetrics(cur);
            Map<String, Object> cmpM = toMetrics(cmp);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("key",                 "week_" + (i + 1));
            row.put("label",               (i + 1) + "주차");
            row.put("range_text",          curRanges.get(i));
            row.put("compare_range_text",  (i < cmpRanges.size()) ? cmpRanges.get(i) : "");
            row.put("days",                dc[0]);
            row.put("is_partial",          dc[1] == 1);
            row.put("current",             curM);
            row.put("compare",             cmpM);
            row.put("diff",                buildDiff(curM, cmpM));
            row.put("per",                 buildPer(curM, cmpM));
            result.add(row);
        }
        return result;
    }

    private List<double[]> weekBuckets(Map<String, Map<String, Object>> daily, String from, String to) {
        LocalDate start = LocalDate.parse(from, FMT);
        LocalDate end   = LocalDate.parse(to,   FMT);
        List<double[]> buckets = new ArrayList<>();
        LocalDate ws = start;
        while (!ws.isAfter(end)) {
            LocalDate we = ws.plusDays(6);
            if (we.isAfter(end)) we = end;
            double[] sums = new double[15];
            for (LocalDate d = ws; !d.isAfter(we); d = d.plusDays(1)) {
                Map<String, Object> row = daily.getOrDefault(d.format(FMT), Collections.emptyMap());
                sums[0]  += num(row, "im");
                sums[1]  += num(row, "clk");
                sums[2]  += num(row, "cst");
                sums[3]  += num(row, "cv");
                sums[4]  += num(row, "cr");
                sums[5]  += num(row, "purchase_cv");
                sums[6]  += num(row, "purchase_cr");
                sums[7]  += num(row, "signup_cv");
                sums[8]  += num(row, "signup_cr");
                sums[9]  += num(row, "cart_cv");
                sums[10] += num(row, "cart_cr");
                sums[11] += num(row, "lead_cv");
                sums[12] += num(row, "lead_cr");
                sums[13] += num(row, "other_cv");
                sums[14] += num(row, "other_cr");
            }
            buckets.add(sums);
            ws = we.plusDays(1);
        }
        return buckets;
    }

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

    private List<int[]> weekDayCounts(String from, String to) {
        LocalDate start = LocalDate.parse(from, FMT);
        LocalDate end   = LocalDate.parse(to,   FMT);
        List<int[]> list = new ArrayList<>();
        LocalDate ws = start;
        while (!ws.isAfter(end)) {
            LocalDate we = ws.plusDays(6);
            boolean partial = we.isAfter(end);
            if (partial) we = end;
            int days = (int)(we.toEpochDay() - ws.toEpochDay() + 1);
            list.add(new int[]{ days, partial ? 1 : 0 });
            ws = we.plusDays(1);
        }
        return list;
    }

    // ─── 월별 trend ──────────────────────────────────────────────────────

    private List<Map<String, Object>> buildMonthlyTrend(
            Map<String, Map<String, Object>> curDaily,
            Map<String, Map<String, Object>> cmpDaily,
            String fromdate, String todate,
            String cfrom, String cto,
            boolean hasCompare) {

        Map<String, double[]> curMonth = groupByMonth(curDaily);
        Map<String, double[]> cmpMonth = hasCompare ? groupByMonth(cmpDaily) : Collections.emptyMap();
        List<String> cmpMonths = new ArrayList<>(cmpMonth.keySet());
        Collections.sort(cmpMonths);
        List<String> months = new ArrayList<>(curMonth.keySet());
        Collections.sort(months);

        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < months.size(); i++) {
            String key = months.get(i); // "2026-06"
            double[] cur = curMonth.get(key);
            double[] cmp = (i < cmpMonths.size()) ? cmpMonth.getOrDefault(cmpMonths.get(i), new double[15]) : new double[15];
            Map<String, Object> curM = toMetrics(cur);
            Map<String, Object> cmpM = toMetrics(cmp);

            String[] parts = key.split("-");
            String label = parts[0] + "년 " + Integer.parseInt(parts[1]) + "월";
            String rangeText = computeMonthRangeText(key, fromdate, todate);
            String cmpRangeText = (hasCompare && i < cmpMonths.size())
                ? computeMonthRangeText(cmpMonths.get(i), cfrom, cto) : "";
            int[] meta = computeMonthDaysPartial(key, fromdate, todate);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("key",                key);
            row.put("label",              label);
            row.put("range_text",         rangeText);
            row.put("compare_range_text", cmpRangeText);
            row.put("days",               meta[0]);
            row.put("is_partial",         meta[1] == 1);
            row.put("current",            curM);
            row.put("compare",            cmpM);
            row.put("diff",               buildDiff(curM, cmpM));
            row.put("per",                buildPer(curM, cmpM));
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

    // ─── 요일별 trend ────────────────────────────────────────────────────

    private List<Map<String, Object>> buildWeekdayTrend(
            Map<String, Map<String, Object>> curDaily,
            Map<String, Map<String, Object>> cmpDaily,
            boolean hasCompare) {

        Map<String, double[]> curDay = groupByDayweek(curDaily);
        Map<String, double[]> cmpDay = hasCompare ? groupByDayweek(cmpDaily) : Collections.emptyMap();

        List<Map<String, Object>> result = new ArrayList<>();
        for (String dk : DAYWEEK_ORDER) {
            double[] cur = curDay.getOrDefault(dk, new double[15]);
            double[] cmp = cmpDay.getOrDefault(dk, new double[15]);
            Map<String, Object> curM = toMetrics(cur);
            Map<String, Object> cmpM = toMetrics(cmp);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("key",                dk);
            row.put("label",              DAYWEEK_LABEL.getOrDefault(dk, dk));
            row.put("range_text",         DAYWEEK_LABEL.getOrDefault(dk, dk));
            row.put("compare_range_text", "");
            row.put("days",               0);
            row.put("is_partial",         false);
            row.put("current",            curM);
            row.put("compare",            cmpM);
            row.put("diff",               buildDiff(curM, cmpM));
            row.put("per",                buildPer(curM, cmpM));
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

    // ─── insights — B안: diff 기준 정렬 ──────────────────────────────────

    private Map<String, List<Map<String, Object>>> buildInsights(List<Map<String, Object>> trend) {
        List<Map<String, Object>> filtered = filterForInsights(trend);
        Map<String, List<Map<String, Object>>> ins = new LinkedHashMap<>();
        ins.put("main",       buildMainInsights(filtered));
        ins.put("cost",       buildCostInsights(filtered));
        ins.put("conversion", buildConversionInsights(filtered));
        ins.put("roas",       buildRoasInsights(filtered));
        return ins;
    }

    private List<Map<String, Object>> filterForInsights(List<Map<String, Object>> trend) {
        List<Map<String, Object>> nonPartial = trend.stream()
            .filter(r -> !Boolean.TRUE.equals(r.get("is_partial")))
            .collect(Collectors.toList());
        return nonPartial.size() >= 3 ? nonPartial : trend;
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
            .filter(w -> dbl((Map<?,?>) w.get("current"), "cst") > 0
                      || dbl((Map<?,?>) w.get("compare"), "cst") > 0)
            .sorted((a, b) -> Double.compare(
                dbl((Map<?,?>) b.get("diff"), "cst"),
                dbl((Map<?,?>) a.get("diff"), "cst")))
            .limit(3)
            .map(w -> toInsightItem(w, "cost"))
            .collect(Collectors.toList());
    }

    private List<Map<String, Object>> buildConversionInsights(List<Map<String, Object>> trend) {
        return trend.stream()
            .filter(w -> dbl((Map<?,?>) w.get("current"), "cst") > 0)
            .sorted((a, b) -> Double.compare(
                dbl((Map<?,?>) a.get("diff"), "cv"),
                dbl((Map<?,?>) b.get("diff"), "cv")))
            .limit(3)
            .map(w -> toInsightItem(w, "conversion"))
            .collect(Collectors.toList());
    }

    private List<Map<String, Object>> buildRoasInsights(List<Map<String, Object>> trend) {
        return trend.stream()
            .filter(w -> dbl((Map<?,?>) w.get("current"), "cst") > 0
                      && dbl((Map<?,?>) w.get("diff"), "purchase_roas") < 0)
            .sorted((a, b) -> Double.compare(
                dbl((Map<?,?>) a.get("diff"), "purchase_roas"),
                dbl((Map<?,?>) b.get("diff"), "purchase_roas")))
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
                double dCst = dbl(diff, "cst");
                double cst  = dbl(cur,  "cst");
                String cDir = dCst > 0 ? "증가" : (dCst < 0 ? "감소" : "변동없음");
                reason    = "광고비 " + formatPrice(Math.abs(dCst)) + "원 " + cDir + " (현재 " + formatPrice(cst) + "원)";
                valueText = (dCst >= 0 ? "+" : "-") + formatPrice(Math.abs(dCst)) + "원";
                tone = dCst > 0 ? "warning" : (dCst < 0 ? "good" : "info");
                break;
            }
            case "conversion": {
                double dCv = dbl(diff, "cv");
                double cv  = dbl(cur,  "cv");
                String cvDir = dCv < 0 ? "감소" : (dCv > 0 ? "증가" : "변동없음");
                reason    = "전환수 " + round2(Math.abs(dCv)) + "건 " + cvDir + " (현재 " + round2(cv) + "건)";
                valueText = (dCv >= 0 ? "+" : "-") + round2(Math.abs(dCv)) + "건";
                tone = dCv < 0 ? "warning" : "info";
                break;
            }
            case "roas": {
                double dPr = dbl(diff, "purchase_roas");
                double pr  = dbl(cur,  "purchase_roas");
                String rDir = dPr < 0 ? "하락" : (dPr > 0 ? "상승" : "변동없음");
                reason    = "구매완료수익률 " + round2(Math.abs(dPr)) + "%p " + rDir + " (현재 " + round2(pr) + "%)";
                valueText = (dPr >= 0 ? "+" : "-") + round2(Math.abs(dPr)) + "%p";
                tone = dPr < 0 ? "warning" : "info";
                break;
            }
            default: { // main
                double dCst = dbl(diff, "cst");
                double dCv  = dbl(diff, "cv");
                reason    = "광고비 " + (dCst >= 0 ? "+" : "-") + formatPrice(Math.abs(dCst)) + "원";
                if (dCv != 0) reason += " · 전환 " + round2(Math.abs(dCv)) + "건" + (dCv < 0 ? " 감소" : " 증가");
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

    // ─── groups (campaign_type 별 + period_items 주차별) ─────────────────

    private List<Map<String, Object>> buildGroups(
            String advid, String media, MediaCfg cfg,
            String from, String to,
            String cfrom, String cto, boolean hasCompare,
            String periodUnit) {
        try {
            // 1. 캠페인 마스터 → campaign_id → type 매핑
            List<Document> masters = mongoService.findCampaigns(advid, cfg.campMasterCol());
            if (masters == null || masters.isEmpty()) return new ArrayList<>();

            Map<String, String> idToType = buildIdToTypeMap(masters, media);
            if (idToType.isEmpty()) return new ArrayList<>();

            // 2. campaign_id × date stat 집계
            Map<String, Map<String, Object>> curByCampDate =
                mongoService.aggregateByCampaignDate(advid, from, to, cfg.campCol(), cfg.advField());
            Map<String, Map<String, Object>> cmpByCampDate = hasCompare
                ? mongoService.aggregateByCampaignDate(advid, cfrom, cto, cfg.campCol(), cfg.advField())
                : Collections.emptyMap();

            // 3. campaign × date × convType 집계 (naver/naverda only)
            Map<String, Map<String, Object>> curCtByCampDate = Collections.emptyMap();
            Map<String, Map<String, Object>> cmpCtByCampDate = Collections.emptyMap();
            if (cfg.convtypeCol() != null) {
                curCtByCampDate = mongoService.aggregateConvtypeByCampaignDate(advid, from, to, cfg.convtypeCol());
                if (hasCompare) {
                    cmpCtByCampDate = mongoService.aggregateConvtypeByCampaignDate(advid, cfrom, cto, cfg.convtypeCol());
                }
            }

            // 4. type별 날짜별 데이터 merge (stat + convtype 통합)
            //    결과: byType[type][date] = double[15]
            Map<String, Map<String, double[]>> curByType = new LinkedHashMap<>();
            Map<String, Map<String, double[]>> cmpByType = new LinkedHashMap<>();
            mergeByType(curByCampDate, curCtByCampDate, idToType, curByType);
            mergeByType(cmpByCampDate, cmpCtByCampDate, idToType, cmpByType);

            // 5. type별 합산 → group row 생성
            Map<String, String> names = GROUP_NAMES.getOrDefault(media, Collections.emptyMap());
            Set<String> seenTypes = new LinkedHashSet<>(curByType.keySet());
            seenTypes.addAll(cmpByType.keySet());

            List<Map<String, Object>> result = new ArrayList<>();
            for (String type : seenTypes) {
                Map<String, double[]> curDateMap = curByType.getOrDefault(type, Collections.emptyMap());
                Map<String, double[]> cmpDateMap = cmpByType.getOrDefault(type, Collections.emptyMap());
                double[] curSums = sumBucket(curDateMap);
                double[] cmpSums = sumBucket(cmpDateMap);

                // cst/cv/cr 모두 0인 경우만 스킵
                boolean curEmpty = curSums[2] <= 0 && curSums[3] <= 0 && curSums[4] <= 0;
                boolean cmpEmpty = cmpSums[2] <= 0 && cmpSums[3] <= 0 && cmpSums[4] <= 0;
                if (curEmpty && cmpEmpty) continue;

                Map<String, Object> curM  = toMetrics(curSums);
                Map<String, Object> cmpM  = toMetrics(cmpSums);
                Map<String, Object> diffM = buildDiff(curM, cmpM);
                Map<String, Object> perM  = buildPer(curM, cmpM);

                String groupName = "unknown".equals(type) ? "기타" : names.getOrDefault(type, type);
                String[] statusInfo = calcGroupStatus(curM, diffM, hasCompare);

                List<Map<String, Object>> periodItems;
                switch (periodUnit) {
                    case "weekday":
                        periodItems = buildGroupWeekdayPeriodItems(curDateMap, cmpDateMap, hasCompare);
                        break;
                    case "month":
                        periodItems = buildGroupMonthPeriodItems(curDateMap, cmpDateMap, from, to, cfrom, cto, hasCompare);
                        break;
                    default: // "week"
                        periodItems = buildGroupPeriodItems(curDateMap, cmpDateMap, from, to, cfrom, cto, hasCompare);
                        break;
                }

                Map<String, Object> g = new LinkedHashMap<>();
                g.put("group_key",    type);
                g.put("group_name",   groupName);
                g.put("status",       statusInfo[0]);
                g.put("status_class", statusInfo[1]);
                g.put("icon",         statusInfo[2]);
                g.put("message",      buildGroupMessage(groupName, curM, diffM, hasCompare));
                g.put("current",      curM);
                g.put("compare",      cmpM);
                g.put("diff",         diffM);
                g.put("per",          perM);
                g.put("period_items", periodItems);
                result.add(g);
            }

            // cst 내림차순
            result.sort((a, b) -> Double.compare(
                dbl((Map<?,?>) b.get("current"), "cst"),
                dbl((Map<?,?>) a.get("current"), "cst")));

            // [검증 로그] trend와 groups 합계 정합성 확인용
            double sumCst = result.stream().mapToDouble(g -> dbl((Map<?,?>) g.get("current"), "cst")).sum();
            double sumCv  = result.stream().mapToDouble(g -> dbl((Map<?,?>) g.get("current"), "cv")).sum();
            double sumCr  = result.stream().mapToDouble(g -> dbl((Map<?,?>) g.get("current"), "cr")).sum();
            double sumPcr = result.stream().mapToDouble(g -> dbl((Map<?,?>) g.get("current"), "purchase_cr")).sum();
            log.info("[groups 검증] advid={} count={} cst={} cv={} cr={} purchase_cr={}",
                advid, result.size(), (long) sumCst, sumCv, (long) sumCr, (long) sumPcr);

            return result;

        } catch (Exception e) {
            log.error("[perioddiagnosisreport] buildGroups 오류 media={} advid={} from={} to={}: {}",
                media, advid, from, to, e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * campaign_id × date stat과 campaign × date × convType을 type별 날짜별로 merge.
     * 결과: byType[type][date] = double[15] (stat + convtype 모두 포함)
     */
    private void mergeByType(Map<String, Map<String, Object>> byCampDate,
                              Map<String, Map<String, Object>> ctByCampDate,
                              Map<String, String> idToType,
                              Map<String, Map<String, double[]>> byType) {
        for (Map.Entry<String, Map<String, Object>> e : byCampDate.entrySet()) {
            String[] parts = e.getKey().split("\\|", 2);
            if (parts.length < 2) continue;
            String cid  = parts[0];
            String date = parts[1];
            String type = idToType.getOrDefault(cid, "unknown");
            Map<String, Object> row = e.getValue();

            double[] arr = new double[15];
            arr[0] = num(row, "im");
            arr[1] = num(row, "clk");
            arr[2] = num(row, "cst");
            arr[3] = num(row, "cv");
            arr[4] = num(row, "cr");

            // convtype 주입 (campaign × date × convType)
            String prefix = cid + "|" + date + "|";
            arr[5]  = dbl(ctByCampDate.getOrDefault(prefix + "purchase",    Collections.emptyMap()), "cnt");
            arr[6]  = dbl(ctByCampDate.getOrDefault(prefix + "purchase",    Collections.emptyMap()), "value");
            arr[7]  = dbl(ctByCampDate.getOrDefault(prefix + "sign_up",     Collections.emptyMap()), "cnt");
            arr[8]  = dbl(ctByCampDate.getOrDefault(prefix + "sign_up",     Collections.emptyMap()), "value");
            arr[9]  = dbl(ctByCampDate.getOrDefault(prefix + "add_to_cart", Collections.emptyMap()), "cnt");
            arr[10] = dbl(ctByCampDate.getOrDefault(prefix + "add_to_cart", Collections.emptyMap()), "value");
            arr[11] = dbl(ctByCampDate.getOrDefault(prefix + "lead",        Collections.emptyMap()), "cnt");
            arr[12] = dbl(ctByCampDate.getOrDefault(prefix + "lead",        Collections.emptyMap()), "value");
            double otherCv = 0, otherCr = 0;
            for (int i = 1; i <= 10; i++) {
                otherCv += dbl(ctByCampDate.getOrDefault(prefix + "custom" + i, Collections.emptyMap()), "cnt");
                otherCr += dbl(ctByCampDate.getOrDefault(prefix + "custom" + i, Collections.emptyMap()), "value");
            }
            arr[13] = otherCv;
            arr[14] = otherCr;

            byType.computeIfAbsent(type, k -> new LinkedHashMap<>())
                  .merge(date, arr, this::mergeArr);
        }
    }

    private double[] sumBucket(Map<String, double[]> dateMap) {
        return dateMap.values().stream().reduce(new double[15], this::mergeArr);
    }

    /**
     * type별 날짜별 데이터로 주차별 bucket 생성 (trend의 weekBuckets와 동일 로직).
     * 데이터 없는 주차도 0값 배열로 포함 → 프론트 구조 안정화.
     */
    private List<double[]> buildTypeWeekBuckets(Map<String, double[]> dateMap, String from, String to) {
        LocalDate start = LocalDate.parse(from, FMT);
        LocalDate end   = LocalDate.parse(to,   FMT);
        List<double[]> buckets = new ArrayList<>();
        LocalDate ws = start;
        while (!ws.isAfter(end)) {
            LocalDate we = ws.plusDays(6);
            if (we.isAfter(end)) we = end;
            double[] sums = new double[15];
            for (LocalDate d = ws; !d.isAfter(we); d = d.plusDays(1)) {
                double[] dayArr = dateMap.get(d.format(FMT));
                if (dayArr != null) {
                    for (int i = 0; i < 15; i++) sums[i] += dayArr[i];
                }
            }
            buckets.add(sums);
            ws = we.plusDays(1);
        }
        return buckets;
    }

    /**
     * group별 period_items 생성 (week 단위).
     * trend와 동일한 key/label/range_text/days/is_partial 순서.
     * 해당 주차에 데이터 없는 group도 0값 row 포함.
     * 정합성: SUM(period_items.current.X) = group.current.X
     */
    private List<Map<String, Object>> buildGroupPeriodItems(
            Map<String, double[]> curDateMap, Map<String, double[]> cmpDateMap,
            String from, String to, String cfrom, String cto, boolean hasCompare) {
        List<double[]> curBuckets = buildTypeWeekBuckets(curDateMap, from, to);
        List<double[]> cmpBuckets = hasCompare ? buildTypeWeekBuckets(cmpDateMap, cfrom, cto) : Collections.emptyList();
        List<String>   curRanges  = weekRanges(from, to);
        List<String>   cmpRanges  = hasCompare ? weekRanges(cfrom, cto) : Collections.emptyList();
        List<int[]>    daysList   = weekDayCounts(from, to);

        List<Map<String, Object>> items = new ArrayList<>();
        for (int i = 0; i < curRanges.size(); i++) {
            double[] cur = curBuckets.get(i);
            double[] cmp = (i < cmpBuckets.size()) ? cmpBuckets.get(i) : new double[15];
            int[] dc = daysList.get(i);
            Map<String, Object> curM = toMetrics(cur);
            Map<String, Object> cmpM = toMetrics(cmp);

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key",                "week_" + (i + 1));
            item.put("label",              (i + 1) + "주차");
            item.put("range_text",         curRanges.get(i));
            item.put("compare_range_text", (i < cmpRanges.size()) ? cmpRanges.get(i) : "");
            item.put("days",               dc[0]);
            item.put("is_partial",         dc[1] == 1);
            item.put("current",            curM);
            item.put("compare",            cmpM);
            item.put("diff",               buildDiff(curM, cmpM));
            item.put("per",                buildPer(curM, cmpM));
            items.add(item);
        }
        return items;
    }

    private List<Map<String, Object>> buildGroupWeekdayPeriodItems(
            Map<String, double[]> curDateMap, Map<String, double[]> cmpDateMap, boolean hasCompare) {
        Map<String, double[]> curByDay = groupByDayweekFromDateMap(curDateMap);
        Map<String, double[]> cmpByDay = hasCompare ? groupByDayweekFromDateMap(cmpDateMap) : Collections.emptyMap();
        List<Map<String, Object>> items = new ArrayList<>();
        for (String dk : DAYWEEK_ORDER) {
            double[] cur = curByDay.getOrDefault(dk, new double[15]);
            double[] cmp = cmpByDay.getOrDefault(dk, new double[15]);
            Map<String, Object> curM = toMetrics(cur);
            Map<String, Object> cmpM = toMetrics(cmp);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key",                dk);
            item.put("label",              DAYWEEK_LABEL.getOrDefault(dk, dk));
            item.put("range_text",         DAYWEEK_LABEL.getOrDefault(dk, dk));
            item.put("compare_range_text", "");
            item.put("days",               0);
            item.put("is_partial",         false);
            item.put("current",            curM);
            item.put("compare",            cmpM);
            item.put("diff",               buildDiff(curM, cmpM));
            item.put("per",                buildPer(curM, cmpM));
            items.add(item);
        }
        return items;
    }

    private Map<String, double[]> groupByDayweekFromDateMap(Map<String, double[]> dateMap) {
        Map<String, double[]> result = new LinkedHashMap<>();
        for (Map.Entry<String, double[]> e : dateMap.entrySet()) {
            try {
                DayOfWeek dow = LocalDate.parse(e.getKey(), FMT).getDayOfWeek();
                String dk = dow.toString().substring(0, 3).toLowerCase();
                result.merge(dk, e.getValue().clone(), this::mergeArr);
            } catch (Exception ignored) {}
        }
        return result;
    }

    private List<Map<String, Object>> buildGroupMonthPeriodItems(
            Map<String, double[]> curDateMap, Map<String, double[]> cmpDateMap,
            String from, String to, String cfrom, String cto, boolean hasCompare) {
        Map<String, double[]> curByMonth = groupByMonthFromDateMap(curDateMap);
        Map<String, double[]> cmpByMonth = hasCompare ? groupByMonthFromDateMap(cmpDateMap) : Collections.emptyMap();
        List<String> curMonths = new ArrayList<>(curByMonth.keySet());
        Collections.sort(curMonths);
        List<String> cmpMonths = new ArrayList<>(cmpByMonth.keySet());
        Collections.sort(cmpMonths);
        List<Map<String, Object>> items = new ArrayList<>();
        for (int i = 0; i < curMonths.size(); i++) {
            String monthKey = curMonths.get(i);
            double[] cur = curByMonth.get(monthKey);
            double[] cmp = (i < cmpMonths.size()) ? cmpByMonth.getOrDefault(cmpMonths.get(i), new double[15]) : new double[15];
            Map<String, Object> curM = toMetrics(cur);
            Map<String, Object> cmpM = toMetrics(cmp);
            String[] parts = monthKey.split("-");
            String label = parts[0] + "년 " + Integer.parseInt(parts[1]) + "월";
            String rangeText = computeMonthRangeText(monthKey, from, to);
            String cmpRangeText = (hasCompare && i < cmpMonths.size())
                ? computeMonthRangeText(cmpMonths.get(i), cfrom, cto) : "";
            int[] meta = computeMonthDaysPartial(monthKey, from, to);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key",                monthKey);
            item.put("label",              label);
            item.put("range_text",         rangeText);
            item.put("compare_range_text", cmpRangeText);
            item.put("days",               meta[0]);
            item.put("is_partial",         meta[1] == 1);
            item.put("current",            curM);
            item.put("compare",            cmpM);
            item.put("diff",               buildDiff(curM, cmpM));
            item.put("per",                buildPer(curM, cmpM));
            items.add(item);
        }
        return items;
    }

    private Map<String, double[]> groupByMonthFromDateMap(Map<String, double[]> dateMap) {
        Map<String, double[]> result = new LinkedHashMap<>();
        for (Map.Entry<String, double[]> e : dateMap.entrySet()) {
            String month = e.getKey().substring(0, 7);
            result.merge(month, e.getValue().clone(), this::mergeArr);
        }
        return result;
    }

    private String computeMonthRangeText(String monthKey, String from, String to) {
        if (from == null || to == null) return monthKey;
        try {
            LocalDate fromDate   = LocalDate.parse(from, FMT);
            LocalDate toDate     = LocalDate.parse(to, FMT);
            String[] parts       = monthKey.split("-");
            LocalDate monthStart = LocalDate.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), 1);
            LocalDate monthEnd   = monthStart.withDayOfMonth(monthStart.lengthOfMonth());
            LocalDate rangeStart = fromDate.isAfter(monthStart) ? fromDate : monthStart;
            LocalDate rangeEnd   = toDate.isBefore(monthEnd)    ? toDate   : monthEnd;
            return rangeStart.format(MFMT) + " ~ " + rangeEnd.format(MFMT);
        } catch (Exception e) {
            return monthKey;
        }
    }

    /** [days, isPartial(0|1)] — 해당 월이 쿼리 기간에서 몇 일 포함되는지, 부분 월인지 */
    private int[] computeMonthDaysPartial(String monthKey, String from, String to) {
        if (from == null || to == null) return new int[]{0, 0};
        try {
            LocalDate fromDate   = LocalDate.parse(from, FMT);
            LocalDate toDate     = LocalDate.parse(to, FMT);
            String[] parts       = monthKey.split("-");
            LocalDate monthStart = LocalDate.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), 1);
            LocalDate monthEnd   = monthStart.withDayOfMonth(monthStart.lengthOfMonth());
            LocalDate rangeStart = fromDate.isAfter(monthStart) ? fromDate : monthStart;
            LocalDate rangeEnd   = toDate.isBefore(monthEnd)    ? toDate   : monthEnd;
            int days    = (int)(rangeEnd.toEpochDay() - rangeStart.toEpochDay() + 1);
            int partial = (!rangeStart.equals(monthStart) || !rangeEnd.equals(monthEnd)) ? 1 : 0;
            return new int[]{days, partial};
        } catch (Exception e) {
            return new int[]{0, 0};
        }
    }

    private Map<String, String> buildIdToTypeMap(List<Document> masters, String media) {
        Map<String, String> map = new LinkedHashMap<>();
        for (Document d : masters) {
            String cid;
            String type;
            switch (media) {
                case "naver": {
                    cid = d.getString("campaignid");
                    if (cid == null) continue;
                    int code = 0;
                    try { code = Integer.parseInt(String.valueOf(d.get("campaigntype"))); } catch (Exception ignored) {}
                    type = NAVER_TYPE_CODE.getOrDefault(code, "unknown");
                    break;
                }
                case "naverda": {
                    // naver_gfa_campaign: cid=String, type=Integer (NaverGfaMasterJob.TYPE_SET)
                    cid = d.getString("cid");
                    if (cid == null) continue;
                    int code = d.getInteger("type", -1);
                    type = GFA_TYPE_CODE.getOrDefault(code, "unknown");
                    break;
                }
                case "kakaosa": {
                    // kakao_sa_campaign: cid=String, 타입 필드 없음 → 단일 그룹 "none"
                    cid = d.getString("cid");
                    if (cid == null) continue;
                    type = "none";
                    break;
                }
                case "kakaomo": {
                    // kakao_mo_campaign: cid=String, type=String (lowercase, e.g. "talk_biz_board")
                    cid = d.getString("cid");
                    if (cid == null) continue;
                    type = d.getString("type");
                    if (type == null || type.isBlank()) type = "unknown";
                    break;
                }
                case "google": {
                    cid = d.getString("cid");
                    if (cid == null) continue;
                    int code = d.getInteger("type", -1);
                    type = GOOGLE_TYPE_CODE.getOrDefault(code, "unknown");
                    break;
                }
                default: {
                    cid = d.getString("cid");
                    if (cid == null) continue;
                    type = d.getString("campaign_type");
                    if (type == null || type.isBlank()) type = "unknown";
                }
            }
            if (type != null) map.put(cid, type);
        }
        return map;
    }

    private String[] calcGroupStatus(Map<String, Object> cur, Map<String, Object> diff, boolean hasCompare) {
        if (!hasCompare) {
            double purchaseRoas = dbl(cur, "purchase_roas");
            if (purchaseRoas > 500) return new String[]{"우수", "success", "bi bi-check-circle"};
            if (purchaseRoas > 0)   return new String[]{"안정", "info",    "bi bi-info-circle"};
            return new String[]{"점검", "neutral", "bi bi-dash-circle"};
        }
        double dCst          = dbl(diff, "cst");
        double dCv           = dbl(diff, "cv");
        double dCpa          = dbl(diff, "cpa");
        double dPurchaseRoas = dbl(diff, "purchase_roas");
        if (dCst > 0 && dCv < 0)                         return new String[]{"핵심 점검", "danger",  "bi bi-exclamation-circle"};
        if (dCst > 0 && dPurchaseRoas < 0)               return new String[]{"주의",     "warning", "bi bi-exclamation-triangle"};
        if (dCv > 0 && dPurchaseRoas > 0 && dCst <= 0)   return new String[]{"우수",     "success", "bi bi-check-circle"};
        if (dCv > 0 && dPurchaseRoas > 0)                 return new String[]{"안정",     "info",    "bi bi-info-circle"};
        if (dCv < -3 || dCpa > 0)                        return new String[]{"주의",     "warning", "bi bi-exclamation-triangle"};
        if (dCv > 0 && dCst <= 0)                        return new String[]{"우수",     "success", "bi bi-check-circle"};
        return new String[]{"안정", "info", "bi bi-info-circle"};
    }

    private String buildGroupMessage(String groupName, Map<String, Object> cur, Map<String, Object> diff, boolean hasCompare) {
        double dCst          = dbl(diff, "cst");
        double dCv           = dbl(diff, "cv");
        double dCr           = dbl(diff, "cr");
        double dPurchaseRoas = dbl(diff, "purchase_roas");
        StringBuilder sb = new StringBuilder(groupName).append(": ");
        if (!hasCompare) {
            sb.append("광고비 ").append(formatPrice(dbl(cur, "cst"))).append("원");
            if (dbl(cur, "cv") > 0) sb.append(", 전환 ").append(round2(dbl(cur, "cv"))).append("건");
        } else if (dCst > 0 && dCv < 0 && dPurchaseRoas < 0) {
            sb.append("광고비 증가 대비 전환수와 구매완료 광고수익률이 함께 하락했습니다.");
        } else if (dCst > 0 && dPurchaseRoas < 0) {
            sb.append("광고비 증가 대비 구매완료 광고수익률이 하락했습니다.");
        } else if (dCv > 0 && dPurchaseRoas < 0) {
            sb.append("전환수는 증가했지만 구매완료 광고수익률이 하락했습니다.");
        } else if (dCv > 0 && dCr > 0 && dPurchaseRoas > 0) {
            double dCpa = dbl(diff, "cpa");
            if (dCst > 0 && dCpa > 0) {
                sb.append("전환수와 구매완료 광고수익률은 개선됐지만, 광고비와 전환당 비용도 함께 증가했습니다.");
            } else {
                sb.append("전환수와 구매완료 광고수익률이 함께 개선되었습니다.");
            }
        } else {
            if (dCst != 0) {
                sb.append("광고비 ").append(formatPrice(Math.abs(dCst))).append("원 ").append(dCst > 0 ? "증가" : "감소");
            }
            if (dCv != 0) {
                if (dCst != 0) sb.append(", ");
                sb.append("전환 ").append(round2(Math.abs(dCv))).append("건 ").append(dCv > 0 ? "증가" : "감소");
            }
            if (dCst > 0 && dCv < 0) sb.append(" — 비용 효율 점검 필요");
        }
        return sb.toString();
    }

    // ─── metrics 빌더 ────────────────────────────────────────────────────

    // v[0]=im, [1]=clk, [2]=cst, [3]=cv, [4]=cr
    // [5]=purchase_cv, [6]=purchase_cr, [7]=signup_cv, [8]=signup_cr
    // [9]=cart_cv, [10]=cart_cr, [11]=lead_cv, [12]=lead_cr
    // [13]=other_cv, [14]=other_cr
    private Map<String, Object> toMetrics(double[] v) {
        double im = v[0], clk = v[1], cst = v[2], cv = v[3], cr = v[4];
        double purchaseCv = v.length > 5  ? v[5]  : 0;
        double purchaseCr = v.length > 6  ? v[6]  : 0;
        double signupCv   = v.length > 7  ? v[7]  : 0;
        double signupCr   = v.length > 8  ? v[8]  : 0;
        double cartCv     = v.length > 9  ? v[9]  : 0;
        double cartCr     = v.length > 10 ? v[10] : 0;
        double leadCv     = v.length > 11 ? v[11] : 0;
        double leadCr     = v.length > 12 ? v[12] : 0;
        double otherCv    = v.length > 13 ? v[13] : 0;
        double otherCr    = v.length > 14 ? v[14] : 0;
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
        m.put("roas",          (cr         > 0 && cst > 0) ? round2(cr         / cst * 100) : 0);
        m.put("purchase_roas", (purchaseCr > 0 && cst > 0) ? round2(purchaseCr / cst * 100) : 0);
        m.put("purchase_cv", round2(purchaseCv)); m.put("purchase_cr", round2(purchaseCr));
        m.put("signup_cv",   round2(signupCv));   m.put("signup_cr",   round2(signupCr));
        m.put("cart_cv",     round2(cartCv));     m.put("cart_cr",     round2(cartCr));
        m.put("lead_cv",     round2(leadCv));     m.put("lead_cr",     round2(leadCr));
        m.put("other_cv",    round2(otherCv));    m.put("other_cr",    round2(otherCr));
        return m;
    }

    private static final String[] DIFF_KEYS = {
        "im","clk","cst","cv","cr","ctr","cpc","cpa","cvr","roas","purchase_roas",
        "purchase_cv","purchase_cr","signup_cv","signup_cr",
        "cart_cv","cart_cr","lead_cv","lead_cr","other_cv","other_cr"
    };

    private static final Set<String> PCT_POINT_KEYS = Set.of("ctr", "cvr", "roas", "purchase_roas");

    private Map<String, Object> buildDiff(Map<String, Object> cur, Map<String, Object> cmp) {
        Map<String, Object> d = new LinkedHashMap<>();
        for (String k : DIFF_KEYS) d.put(k, round2(dbl(cur, k) - dbl(cmp, k)));
        return d;
    }

    private Map<String, Object> buildPer(Map<String, Object> cur, Map<String, Object> cmp) {
        Map<String, Object> p = new LinkedHashMap<>();
        for (String k : DIFF_KEYS) {
            if (PCT_POINT_KEYS.contains(k)) {
                p.put(k, 0);
            } else {
                double a = dbl(cur, k), b = dbl(cmp, k);
                p.put(k, b > 0 ? round2((a - b) / b * 100) : 0);
            }
        }
        return p;
    }

    // ─── 헬퍼 ────────────────────────────────────────────────────────────

    private void mergeConvtypeIntoDaily(Map<String, Map<String, Object>> daily,
                                         Map<String, Map<String, Object>> ctMap) {
        for (Map.Entry<String, Map<String, Object>> e : daily.entrySet()) {
            String date = e.getKey();
            Map<String, Object> row = e.getValue();
            Map<String, Object> purchase = ctMap.getOrDefault(date + "|purchase",    Collections.emptyMap());
            Map<String, Object> signup   = ctMap.getOrDefault(date + "|sign_up",     Collections.emptyMap());
            Map<String, Object> cart     = ctMap.getOrDefault(date + "|add_to_cart", Collections.emptyMap());
            Map<String, Object> lead     = ctMap.getOrDefault(date + "|lead",        Collections.emptyMap());
            row.put("purchase_cv", dbl(purchase, "cnt"));  row.put("purchase_cr", dbl(purchase, "value"));
            row.put("signup_cv",   dbl(signup,   "cnt"));  row.put("signup_cr",   dbl(signup,   "value"));
            row.put("cart_cv",     dbl(cart,     "cnt"));  row.put("cart_cr",     dbl(cart,     "value"));
            row.put("lead_cv",     dbl(lead,     "cnt"));  row.put("lead_cr",     dbl(lead,     "value"));
            double otherCv = 0, otherCr = 0;
            for (int i = 1; i <= 10; i++) {
                Map<String, Object> custom = ctMap.getOrDefault(date + "|custom" + i, Collections.emptyMap());
                otherCv += dbl(custom, "cnt");
                otherCr += dbl(custom, "value");
            }
            row.put("other_cv", otherCv);
            row.put("other_cr", otherCr);
        }
    }

    private double[] toArr(Map<String, Object> row) {
        return new double[]{
            num(row,"im"), num(row,"clk"), num(row,"cst"), num(row,"cv"), num(row,"cr"),
            num(row,"purchase_cv"), num(row,"purchase_cr"),
            num(row,"signup_cv"),   num(row,"signup_cr"),
            num(row,"cart_cv"),     num(row,"cart_cr"),
            num(row,"lead_cv"),     num(row,"lead_cr"),
            num(row,"other_cv"),    num(row,"other_cr")
        };
    }

    private double[] mergeArr(double[] a, double[] b) {
        double[] r = new double[a.length];
        for (int i = 0; i < a.length; i++) r[i] = a[i] + b[i];
        return r;
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

    private String sign(double v) { return v >= 0 ? "+" : ""; }

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
