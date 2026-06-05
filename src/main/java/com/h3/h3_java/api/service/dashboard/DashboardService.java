package com.h3.h3_java.api.service.dashboard;

import com.h3.h3_java.api.dto.AccountDto;
import com.h3.h3_java.api.mapper.AccountMapper;
import com.h3.h3_java.raw.mongo.DashboardMongoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final AccountMapper accountMapper;
    private final DashboardMongoService mongoService;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // ─── 매체별 요약 (summarymedia) ──────────────────────────────────────────

    public Map<String, Object> getSummaryMedia(String userId, String fromdate, String todate, String media) {
        AccountDto acc = accountMapper.selectByUserId(userId);
        if (acc == null) return Map.of("result", "failed", "status", "1009", "errormessage", "계정 없음");

        boolean[] m = parseMedia(media);
        Map<String, Object> result = new LinkedHashMap<>();

        if (m[0]) result.put("naver",   calcMediaStat(acc.getAccountNaverCustomer(), fromdate, todate, "naver_campaign_daily",     true,  "naver_campaign_convtype"));
        if (m[1]) result.put("kakaosa", calcMediaStat(acc.getAccountKakaosa(),       fromdate, todate, "kakao_sa_campaign_daily",  false, null));
        if (m[2]) result.put("kakaomo", calcMediaStat(acc.getAccountKakaomoment(),   fromdate, todate, "kakao_mo_campaign_daily",  false, null));
        if (m[3]) result.put("naverda", calcMediaStat(acc.getAccountGfa(),           fromdate, todate, "naver_gfa_campaign_daily", true,  "naver_gfa_campaign_convtype"));
        if (m[4]) result.put("google",  calcMediaStat(acc.getAccountGoogle(),        fromdate, todate, "google_campaign_daily",    false, null));

        return Map.of("result", "success", "status", "200", "data", result);
    }

    // ─── 전체 요약 (summary) ─────────────────────────────────────────────────

    public Map<String, Object> getSummary(String userId, String fromdate, String todate,
                                          String comparefromdate, String comparetodate, String media) {
        AccountDto acc = accountMapper.selectByUserId(userId);
        if (acc == null) return Map.of("result", "failed", "status", "1009", "errormessage", "계정 없음");

        boolean[] m   = parseMedia(media);
        String cfrom  = (comparefromdate != null && !comparefromdate.isEmpty()) ? comparefromdate : fromdate;
        String cto    = (comparetodate  != null && !comparetodate.isEmpty())  ? comparetodate  : todate;

        Map<String, Object> cur  = aggregateAllMedia(acc, fromdate,  todate, m);
        Map<String, Object> comp = aggregateAllMedia(acc, cfrom,     cto,    m);

        Map<String, Object> stat = calcStat(cur);
        stat.put("cp", buildCp(cur, comp));
        return Map.of("result", "success", "status", "200", "data", stat);
    }

    // ─── 날짜별 통계 (period) ────────────────────────────────────────────────

    public Map<String, Object> getPeriod(String userId, String fromdate, String todate, String media) {
        AccountDto acc = accountMapper.selectByUserId(userId);
        if (acc == null) return Map.of("result", "failed", "status", "1009", "errormessage", "계정 없음");

        boolean[] m = parseMedia(media);

        // 매체별 날짜별 데이터 수집
        Map<String, Map<String, Object>> naverMap   = m[0] ? mongoService.aggregateByDate(acc.getAccountNaverCustomer(),  fromdate, todate, "naver_campaign_daily")     : Collections.emptyMap();
        Map<String, Map<String, Object>> kakaoSaMap = m[1] ? mongoService.aggregateByDate(acc.getAccountKakaosa(),        fromdate, todate, "kakao_sa_campaign_daily")  : Collections.emptyMap();
        Map<String, Map<String, Object>> kakaoMoMap = m[2] ? mongoService.aggregateByDate(acc.getAccountKakaomoment(),    fromdate, todate, "kakao_mo_campaign_daily")  : Collections.emptyMap();
        Map<String, Map<String, Object>> gfaMap     = m[3] ? mongoService.aggregateByDate(acc.getAccountGfa(),            fromdate, todate, "naver_gfa_campaign_daily") : Collections.emptyMap();
        Map<String, Map<String, Object>> googleMap  = m[4] ? mongoService.aggregateByDate(acc.getAccountGoogle(),         fromdate, todate, "google_campaign_daily")    : Collections.emptyMap();

        // 날짜 범위 순회, 매일 합산
        List<Map<String, Object>> graph = new ArrayList<>();
        LocalDate cur = LocalDate.parse(fromdate, FMT);
        LocalDate end = LocalDate.parse(todate, FMT);
        while (!cur.isAfter(end)) {
            String date = cur.format(FMT);
            double im  = getVal(naverMap, date, "im")  * 1 + getVal(kakaoSaMap, date, "im")  + getVal(kakaoMoMap, date, "im")  + getVal(gfaMap, date, "im")  + getVal(googleMap, date, "im");
            double clk = getVal(naverMap, date, "clk") + getVal(kakaoSaMap, date, "clk") + getVal(kakaoMoMap, date, "clk") + getVal(gfaMap, date, "clk") + getVal(googleMap, date, "clk");
            double cst = getVal(naverMap, date, "cst") * 1.1
                       + getVal(kakaoSaMap, date, "cst") + getVal(kakaoMoMap, date, "cst")
                       + getVal(gfaMap, date, "cst") + getVal(googleMap, date, "cst");
            double cv  = getVal(naverMap, date, "cv")  + getVal(kakaoSaMap, date, "cv")  + getVal(kakaoMoMap, date, "cv")  + getVal(gfaMap, date, "cv")  + getVal(googleMap, date, "cv");
            double cr  = getVal(naverMap, date, "cr")  + getVal(kakaoSaMap, date, "cr")  + getVal(kakaoMoMap, date, "cr")  + getVal(gfaMap, date, "cr")  + getVal(googleMap, date, "cr");

            Map<String, Object> day = new LinkedHashMap<>();
            day.put("daily_dt", date);
            day.put("im",  Math.round(im));
            day.put("clk", Math.round(clk));
            day.put("cst", Math.round(cst));
            day.put("cv",  Math.round(cv));
            day.put("cr",  Math.round(cr));
            day.putAll(calcMetrics(im, clk, cst, cv, cr));
            graph.add(day);
            cur = cur.plusDays(1);
        }
        return Map.of("result", "success", "status", "200", "data", graph);
    }

    // ─── 내부 헬퍼 ───────────────────────────────────────────────────────────

    private Map<String, Object> calcMediaStat(String advid, String from, String to,
                                               String collection, boolean hasConvtype, String convCollection) {
        if (advid == null || advid.isBlank()) return emptyMediaStat();
        Map<String, Object> raw = mongoService.aggregateTotal(advid, from, to, collection);
        double im  = toDouble(raw, "im");
        double clk = toDouble(raw, "clk");
        double cst = "naver_campaign_daily".equals(collection) || "naver_gfa_campaign_daily".equals(collection)
                     ? toDouble(raw, "cst") * 1.1 : toDouble(raw, "cst");
        double cv  = toDouble(raw, "cv");
        double cr  = toDouble(raw, "cr");

        double purchaseCv = 0, purchaseCr = 0, signupCv = 0, signupCr = 0;
        double cartCv = 0, cartCr = 0, leadCv = 0, leadCr = 0, otherCv = 0, otherCr = 0;

        if (hasConvtype && convCollection != null) {
            Map<String, Map<String, Object>> ct = mongoService.aggregateConvtype(advid, from, to, convCollection);
            purchaseCv = ctCnt(ct, "purchase"); purchaseCr = ctVal(ct, "purchase");
            signupCv   = ctCnt(ct, "sign_up");  signupCr   = ctVal(ct, "sign_up");
            cartCv     = ctCnt(ct, "add_to_cart"); cartCr  = ctVal(ct, "add_to_cart");
            leadCv     = ctCnt(ct, "lead");     leadCr     = ctVal(ct, "lead");
            for (Map.Entry<String, Map<String, Object>> e : ct.entrySet()) {
                String code = e.getKey();
                if (!Set.of("purchase", "sign_up", "add_to_cart", "lead").contains(code)) {
                    otherCv += ctCnt(ct, code);
                    otherCr += ctVal(ct, code);
                }
            }
        }

        Map<String, Object> stat = new LinkedHashMap<>();
        stat.put("im",  Math.round(im));  stat.put("clk", Math.round(clk));
        stat.put("cst", Math.round(cst)); stat.put("cv",  Math.round(cv));
        stat.put("cr",  Math.round(cr));
        stat.putAll(calcMetrics(im, clk, cst, cv, cr));
        stat.put("purchase_cv", Math.round(purchaseCv)); stat.put("purchase_cr", Math.round(purchaseCr));
        stat.put("signup_cv",   Math.round(signupCv));   stat.put("signup_cr",   Math.round(signupCr));
        stat.put("cart_cv",     Math.round(cartCv));     stat.put("cart_cr",     Math.round(cartCr));
        stat.put("lead_cv",     Math.round(leadCv));     stat.put("lead_cr",     Math.round(leadCr));
        stat.put("other_cv",    Math.round(otherCv));    stat.put("other_cr",    Math.round(otherCr));
        stat.put("purchase_roas", (purchaseCr > 0 && cst > 0) ? fmt(purchaseCr / cst * 100) : 0);
        return stat;
    }

    private Map<String, Object> aggregateAllMedia(AccountDto acc, String from, String to, boolean[] m) {
        double im = 0, clk = 0, cst = 0, cv = 0, cr = 0;
        if (m[0] && acc.getAccountNaverCustomer() != null) {
            Map<String, Object> r = mongoService.aggregateTotal(acc.getAccountNaverCustomer(), from, to, "naver_campaign_daily");
            im += toDouble(r, "im"); clk += toDouble(r, "clk"); cst += toDouble(r, "cst") * 1.1; cv += toDouble(r, "cv"); cr += toDouble(r, "cr");
        }
        if (m[1] && acc.getAccountKakaosa() != null) {
            Map<String, Object> r = mongoService.aggregateTotal(acc.getAccountKakaosa(), from, to, "kakao_sa_campaign_daily");
            im += toDouble(r, "im"); clk += toDouble(r, "clk"); cst += toDouble(r, "cst"); cv += toDouble(r, "cv"); cr += toDouble(r, "cr");
        }
        if (m[2] && acc.getAccountKakaomoment() != null) {
            Map<String, Object> r = mongoService.aggregateTotal(acc.getAccountKakaomoment(), from, to, "kakao_mo_campaign_daily");
            im += toDouble(r, "im"); clk += toDouble(r, "clk"); cst += toDouble(r, "cst"); cv += toDouble(r, "cv"); cr += toDouble(r, "cr");
        }
        if (m[3] && acc.getAccountGfa() != null) {
            Map<String, Object> r = mongoService.aggregateTotal(acc.getAccountGfa(), from, to, "naver_gfa_campaign_daily");
            im += toDouble(r, "im"); clk += toDouble(r, "clk"); cst += toDouble(r, "cst") * 1.1; cv += toDouble(r, "cv"); cr += toDouble(r, "cr");
        }
        if (m[4] && acc.getAccountGoogle() != null) {
            Map<String, Object> r = mongoService.aggregateTotal(acc.getAccountGoogle(), from, to, "google_campaign_daily");
            im += toDouble(r, "im"); clk += toDouble(r, "clk"); cst += toDouble(r, "cst"); cv += toDouble(r, "cv"); cr += toDouble(r, "cr");
        }
        return Map.of("im", im, "clk", clk, "cst", cst, "cv", cv, "cr", cr);
    }

    private Map<String, Object> buildCp(Map<String, Object> cur, Map<String, Object> comp) {
        String[] keys = {"im", "clk", "cst", "cv", "cr"};
        Map<String, Object> cp = new LinkedHashMap<>();
        for (String k : keys) {
            double c = toDouble(cur, k), p = toDouble(comp, k);
            double per = (p != 0) ? fmt((c - p) / p * 100) : 0;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("val", c); m.put("cp", p); m.put("per", per);
            cp.put(k, m);
        }
        return cp;
    }

    private Map<String, Object> calcStat(Map<String, Object> raw) {
        double im = toDouble(raw, "im"), clk = toDouble(raw, "clk"), cst = toDouble(raw, "cst"),
               cv = toDouble(raw, "cv"), cr = toDouble(raw, "cr");
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("im",  Math.round(im)); s.put("clk", Math.round(clk));
        s.put("cst", Math.round(cst)); s.put("cv", Math.round(cv)); s.put("cr", Math.round(cr));
        s.putAll(calcMetrics(im, clk, cst, cv, cr));
        return s;
    }

    private Map<String, Object> calcMetrics(double im, double clk, double cst, double cv, double cr) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ctr",  (clk > 0 && im > 0)  ? fmt(clk / im  * 100) : 0);
        m.put("cpc",  (cst > 0 && clk > 0) ? fmt(cst / clk)       : 0);
        m.put("cpa",  (cst > 0 && cv > 0)  ? fmt(cst / cv)        : 0);
        m.put("cvr",  (cv > 0 && clk > 0)  ? fmt(cv  / clk * 100) : 0);
        m.put("roas", (cr > 0 && cst > 0)  ? fmt(cr  / cst * 100) : 0);
        return m;
    }

    private boolean[] parseMedia(String media) {
        if (media == null || media.isBlank()) return new boolean[]{true, true, true, true, true};
        String[] parts = media.split(",");
        boolean[] r = new boolean[5];
        for (int i = 0; i < Math.min(5, parts.length); i++) r[i] = "1".equals(parts[i].trim());
        return r;
    }

    private double getVal(Map<String, Map<String, Object>> map, String date, String key) {
        Map<String, Object> row = map.get(date);
        return row != null ? toDouble(row, key) : 0.0;
    }

    private double toDouble(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return (v instanceof Number) ? ((Number) v).doubleValue() : 0.0;
    }

    private double ctCnt(Map<String, Map<String, Object>> ct, String code) {
        return ct.containsKey(code) ? toDouble(ct.get(code), "cnt") : 0.0;
    }

    private double ctVal(Map<String, Map<String, Object>> ct, String code) {
        return ct.containsKey(code) ? toDouble(ct.get(code), "value") : 0.0;
    }

    private double fmt(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private Map<String, Object> emptyMediaStat() {
        Map<String, Object> r = new LinkedHashMap<>();
        for (String k : List.of("im","clk","cst","cv","cr","ctr","cpc","cpa","cvr","roas",
            "purchase_cv","purchase_cr","signup_cv","signup_cr","cart_cv","cart_cr",
            "lead_cv","lead_cr","other_cv","other_cr","purchase_roas")) r.put(k, 0);
        return r;
    }
}
