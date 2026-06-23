package com.h3.h3_java.api.service.analysis;

import com.h3.h3_java.api.dto.AccountDto;
import com.h3.h3_java.raw.mongo.AccountMongoService;
import com.h3.h3_java.raw.mongo.DashboardMongoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class MediaReportService {

    private final AccountMongoService accountMongo;
    private final DashboardMongoService mongoService;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public Map<String, Object> getMediaReport(String userId, String from, String to, String cfrom, String cto) {
        AccountDto acc = accountMongo.findAccountDtoByUserId(userId);
        if (acc == null) return fail("1009", "계정을 확인해 주세요.");

        String comparefrom = (cfrom != null && !cfrom.isBlank()) ? cfrom : from;
        String compareto   = (cto   != null && !cto.isBlank())   ? cto   : to;

        // 현재 기간 - 각 매체 일별 데이터
        Map<String, Map<String, Object>> naverData   = fetchMediaDaily(acc.getAccountNaverCustomer(), from, to, "naver_campaign_daily",     "naver_campaign_convtype");
        Map<String, Map<String, Object>> kakaosaData = fetchMediaDaily(acc.getAccountKakaosa(),       from, to, "kakao_sa_campaign_daily",  null);
        Map<String, Map<String, Object>> kakaomoData = fetchMediaDaily(acc.getAccountKakaomoment(),   from, to, "kakao_mo_campaign_daily",  null);
        Map<String, Map<String, Object>> naverdaData = fetchMediaDaily(acc.getAccountGfa(),           from, to, "naver_gfa_campaign_daily", "naver_gfa_campaign_convtype");
        Map<String, Map<String, Object>> googleData  = fetchMediaDaily(acc.getAccountGoogle(),        from, to, "google_campaign_daily",    null);

        if (naverData.isEmpty()) return noData();

        // 비교 기간 - 각 매체 일별 데이터
        Map<String, Map<String, Object>> cNaverData   = fetchMediaDaily(acc.getAccountNaverCustomer(), comparefrom, compareto, "naver_campaign_daily",     "naver_campaign_convtype");
        Map<String, Map<String, Object>> cKakaosaData = fetchMediaDaily(acc.getAccountKakaosa(),       comparefrom, compareto, "kakao_sa_campaign_daily",  null);
        Map<String, Map<String, Object>> cKakaomoData = fetchMediaDaily(acc.getAccountKakaomoment(),   comparefrom, compareto, "kakao_mo_campaign_daily",  null);
        Map<String, Map<String, Object>> cNaverdaData = fetchMediaDaily(acc.getAccountGfa(),           comparefrom, compareto, "naver_gfa_campaign_daily", "naver_gfa_campaign_convtype");
        Map<String, Map<String, Object>> cGoogleData  = fetchMediaDaily(acc.getAccountGoogle(),        comparefrom, compareto, "google_campaign_daily",    null);

        // total 현재/비교 기간
        Map<String, Object> total  = buildTotal(from, to, naverData, kakaosaData, kakaomoData, naverdaData, googleData);
        Map<String, Object> ctotal = buildTotal(comparefrom, compareto, cNaverData, cKakaosaData, cKakaomoData, cNaverdaData, cGoogleData);

        // cp 계산 (summary.all 현재 vs 비교)
        @SuppressWarnings("unchecked")
        Map<String, Object> summaryAll  = (Map<String, Object>) ((Map<String, Object>) total.get("summary")).get("all");
        @SuppressWarnings("unchecked")
        Map<String, Object> cSummaryAll = (Map<String, Object>) ((Map<String, Object>) ctotal.get("summary")).get("all");
        summaryAll.put("cp", buildCp2(summaryAll, cSummaryAll));

        // 응답 조립
        Map<String, Object> sa = new LinkedHashMap<>();
        sa.put("naver",   naverData);
        sa.put("kakaosa", kakaosaData);
        sa.put("google",  googleData);

        Map<String, Object> dsp = new LinkedHashMap<>();
        dsp.put("kakaomo", kakaomoData);
        dsp.put("naverda", naverdaData);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", total);
        data.put("sa",    sa);
        data.put("dsp",   dsp);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("result", "success");
        res.put("status", "200");
        res.put("data",   data);
        return res;
    }

    // ─── 매체별 일별 데이터 조회 ─────────────────────────────────────────────

    private Map<String, Map<String, Object>> fetchMediaDaily(String advid, String from, String to,
                                                               String dailyCol, String convtypeCol) {
        if (advid == null || advid.isBlank()) return zeroFilledDates(from, to, convtypeCol != null);

        Map<String, Map<String, Object>> daily = mongoService.aggregateByDate(advid, from, to, dailyCol);
        if (daily.isEmpty()) return zeroFilledDates(from, to, convtypeCol != null);

        // 날짜 범위 내 데이터 없는 날 0으로 채움
        LocalDate d = LocalDate.parse(from, FMT);
        LocalDate end = LocalDate.parse(to, FMT);
        while (!d.isAfter(end)) {
            String date = d.format(FMT);
            if (!daily.containsKey(date)) daily.put(date, emptyRow(convtypeCol != null));
            d = d.plusDays(1);
        }

        if (convtypeCol != null) {
            Map<String, Map<String, Object>> convByDate = mongoService.aggregateConvtypeByDate(advid, from, to, convtypeCol);
            for (Map.Entry<String, Map<String, Object>> e : daily.entrySet()) {
                mergeConvtype(e.getValue(), e.getKey(), convByDate);
            }
        }

        for (Map<String, Object> row : daily.values()) {
            enrichRow(row, convtypeCol != null);
        }
        return new TreeMap<>(daily);
    }

    private Map<String, Map<String, Object>> zeroFilledDates(String from, String to, boolean hasConvtype) {
        Map<String, Map<String, Object>> result = new TreeMap<>();
        LocalDate d = LocalDate.parse(from, FMT);
        LocalDate end = LocalDate.parse(to, FMT);
        while (!d.isAfter(end)) {
            result.put(d.format(FMT), emptyRow(hasConvtype));
            d = d.plusDays(1);
        }
        return result;
    }

    private Map<String, Object> emptyRow(boolean hasConvtype) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("im", 0L); row.put("clk", 0L); row.put("cst", 0L);
        row.put("cv", 0L); row.put("cr", 0L);
        if (hasConvtype) {
            row.put("purchase_cv", 0L); row.put("purchase_cr", 0L);
            row.put("signup_cv",   0L); row.put("signup_cr",   0L);
            row.put("cart_cv",     0L); row.put("cart_cr",     0L);
            row.put("lead_cv",     0L); row.put("lead_cr",     0L);
            row.put("other_cv",    0L); row.put("other_cr",    0L);
        }
        row.put("ctr", 0); row.put("cpc", 0); row.put("cpa", 0);
        row.put("cvr", 0); row.put("roas", 0);
        if (hasConvtype) row.put("purchase_roas", 0);
        return row;
    }

    private void mergeConvtype(Map<String, Object> row, String date, Map<String, Map<String, Object>> convByDate) {
        double pCv=0, pCr=0, sCv=0, sCr=0, cCv=0, cCr=0, lCv=0, lCr=0, oCv=0, oCr=0;
        for (Map.Entry<String, Map<String, Object>> e : convByDate.entrySet()) {
            String key = e.getKey();
            if (!key.startsWith(date + "|")) continue;
            String code = key.substring(date.length() + 1);
            double cnt = dv(e.getValue(), "cnt"), val = dv(e.getValue(), "value");
            switch (code) {
                case "purchase"    -> { pCv += cnt; pCr += val; }
                case "sign_up"     -> { sCv += cnt; sCr += val; }
                case "add_to_cart" -> { cCv += cnt; cCr += val; }
                case "lead"        -> { lCv += cnt; lCr += val; }
                default            -> { oCv += cnt; oCr += val; }
            }
        }
        row.put("purchase_cv", Math.round(pCv)); row.put("purchase_cr", Math.round(pCr));
        row.put("signup_cv",   Math.round(sCv)); row.put("signup_cr",   Math.round(sCr));
        row.put("cart_cv",     Math.round(cCv)); row.put("cart_cr",     Math.round(cCr));
        row.put("lead_cv",     Math.round(lCv)); row.put("lead_cr",     Math.round(lCr));
        row.put("other_cv",    Math.round(oCv)); row.put("other_cr",    Math.round(oCr));
    }

    private void enrichRow(Map<String, Object> row, boolean hasConvtype) {
        double im=dv(row,"im"), clk=dv(row,"clk"), cst=dv(row,"cst"), cv=dv(row,"cv"), cr=dv(row,"cr");
        row.put("im",  Math.round(im));
        row.put("clk", Math.round(clk));
        row.put("cst", Math.round(cst));
        row.put("cv",  Math.round(cv));
        row.put("cr",  Math.round(cr));
        row.put("ctr",  (clk>0&&im>0)  ? fmt2(clk/im*100)  : 0);
        row.put("cpc",  (cst>0&&clk>0) ? fmt2(cst/clk)     : 0);
        row.put("cpa",  (cst>0&&cv>0)  ? fmt2(cst/cv)      : 0);
        row.put("cvr",  (cv>0&&clk>0)  ? fmt2(cv/clk*100)  : 0);
        row.put("roas", (cr>0&&cst>0)  ? fmt2(cr/cst*100)  : 0);
        if (hasConvtype) {
            double pCr = dv(row, "purchase_cr");
            row.put("purchase_roas", (pCr>0&&cst>0) ? fmt2(pCr/cst*100) : 0);
        }
    }

    // ─── Total 빌드 ──────────────────────────────────────────────────────────

    private Map<String, Object> buildTotal(String from, String to,
                                            Map<String, Map<String, Object>> naver,
                                            Map<String, Map<String, Object>> kakaosa,
                                            Map<String, Map<String, Object>> kakaomo,
                                            Map<String, Map<String, Object>> naverda,
                                            Map<String, Map<String, Object>> google) {
        // from~to 범위의 모든 날짜 생성 (PHP 날짜 enumerate 방식)
        Set<String> dates = new TreeSet<>();
        LocalDate d = LocalDate.parse(from, FMT);
        LocalDate end = LocalDate.parse(to, FMT);
        while (!d.isAfter(end)) { dates.add(d.format(FMT)); d = d.plusDays(1); }

        // summary 누계 (인덱스: 0=im,1=clk,2=cst,3=cv,4=cr, 5~14=convtype)
        double[] all = new double[15], sa = new double[15], dsp = new double[15];

        Map<String, Object> total = new LinkedHashMap<>();

        for (String date : dates) {
            double[] n  = toRow(naver.get(date));
            double[] ks = toRow(kakaosa.get(date));
            double[] km = toRow(kakaomo.get(date));
            double[] nd = toRow(naverda.get(date));
            double[] g  = toRow(google.get(date));

            // 5개 매체 합산
            double[] t = new double[15];
            for (int i = 0; i < 15; i++) t[i] = n[i]+ks[i]+km[i]+nd[i]+g[i];

            // summary 누계
            for (int i = 0; i < 15; i++) {
                all[i] += t[i];
                sa[i]  += n[i]+ks[i]+g[i];
                dsp[i] += km[i]+nd[i];
            }

            // 날짜별 합계 row
            Map<String, Object> dateRow = new LinkedHashMap<>();
            long cst = Math.round(t[2]);
            dateRow.put("im",(long)t[0]); dateRow.put("clk",(long)t[1]); dateRow.put("cst",cst);
            dateRow.put("cv",Math.round(t[3])); dateRow.put("cr",Math.round(t[4]));
            dateRow.put("purchase_cv",(long)t[5]); dateRow.put("purchase_cr",(long)t[6]);
            dateRow.put("signup_cv",  (long)t[7]); dateRow.put("signup_cr",  (long)t[8]);
            dateRow.put("cart_cv",    (long)t[9]); dateRow.put("cart_cr",    (long)t[10]);
            dateRow.put("lead_cv",    (long)t[11]);dateRow.put("lead_cr",    (long)t[12]);
            dateRow.put("other_cv",   (long)t[13]);dateRow.put("other_cr",   (long)t[14]);
            dateRow.put("ctr",  (t[1]>0&&t[0]>0) ? fmt2(t[1]/t[0]*100)  : 0);
            dateRow.put("cpc",  (cst>0&&t[1]>0)  ? fmt2((double)cst/t[1]): 0);
            dateRow.put("cpa",  (cst>0&&t[3]>0)  ? fmt2((double)cst/t[3]): 0);
            dateRow.put("cvr",  (t[3]>0&&t[1]>0) ? fmt2(t[3]/t[1]*100)  : 0);
            dateRow.put("roas", (t[4]>0&&cst>0)  ? fmt2(t[4]/cst*100)   : 0);
            dateRow.put("purchase_roas", (t[6]>0&&cst>0) ? fmt2(t[6]/cst*100) : 0);
            total.put(date, dateRow);
        }

        // summary 계산
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("all", buildSummary(all));
        summary.put("sa",  buildSummary(sa));
        summary.put("dsp", buildSummary(dsp));
        total.put("summary", summary);

        return total;
    }

    // 일별 데이터에서 [im,clk,cst,cv,cr, purchase_cv/cr, signup_cv/cr, cart_cv/cr, lead_cv/cr, other_cv/cr] 추출
    private double[] toRow(Map<String, Object> r) {
        if (r == null) return new double[15];
        return new double[]{
            dv(r,"im"), dv(r,"clk"), dv(r,"cst"), dv(r,"cv"), dv(r,"cr"),
            dv(r,"purchase_cv"), dv(r,"purchase_cr"),
            dv(r,"signup_cv"),   dv(r,"signup_cr"),
            dv(r,"cart_cv"),     dv(r,"cart_cr"),
            dv(r,"lead_cv"),     dv(r,"lead_cr"),
            dv(r,"other_cv"),    dv(r,"other_cr")
        };
    }

    private Map<String, Object> buildSummary(double[] s) {
        long cst = Math.round(s[2]);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("im",(long)s[0]); r.put("clk",(long)s[1]); r.put("cst",cst);
        r.put("cv",Math.round(s[3])); r.put("cr",Math.round(s[4]));
        r.put("ctr",  (s[1]>0&&s[0]>0) ? fmt2(s[1]/s[0]*100)    : 0);
        r.put("cpc",  (cst>0&&s[1]>0)  ? fmt2((double)cst/s[1]) : 0);
        r.put("cpa",  (cst>0&&s[3]>0)  ? fmt2((double)cst/s[3]) : 0);
        r.put("cvr",  (s[3]>0&&s[1]>0) ? fmt2(s[3]/s[1]*100)    : 0);
        r.put("roas", (s[4]>0&&cst>0)  ? fmt2(s[4]/cst*100)     : 0);
        r.put("purchase_cv",(long)s[5]); r.put("purchase_cr",(long)s[6]);
        r.put("signup_cv",  (long)s[7]); r.put("signup_cr",  (long)s[8]);
        r.put("cart_cv",    (long)s[9]); r.put("cart_cr",    (long)s[10]);
        r.put("lead_cv",    (long)s[11]);r.put("lead_cr",    (long)s[12]);
        r.put("other_cv",   (long)s[13]);r.put("other_cr",   (long)s[14]);
        r.put("purchase_roas", (s[6]>0&&cst>0) ? fmt2(s[6]/cst*100) : 0);
        return r;
    }

    // ─── CP 계산 (getCp2 equivalent) ────────────────────────────────────────

    private Map<String, Object> buildCp2(Map<String, Object> cur, Map<String, Object> comp) {
        String[] keys = {"im","clk","cst","cv","cr","ctr","cpc","cpa","cvr","roas",
                         "purchase_cv","purchase_cr","signup_cv","signup_cr",
                         "cart_cv","cart_cr","lead_cv","lead_cr","other_cv","other_cr","purchase_roas"};
        Map<String, Object> per = new LinkedHashMap<>();
        Map<String, Object> cp  = new LinkedHashMap<>();
        for (String k : keys) {
            double a = dv(cur, k), b = dv(comp, k);
            if (a > 0 && b > 0) {
                per.put(k, fmt2((a-b)/b*100));
                cp.put(k, comp.get(k));
            } else {
                per.put(k, 0);
                cp.put(k, 0);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("per", per);
        result.put("cp",  cp);
        return result;
    }

    // ─── 공통 헬퍼 ──────────────────────────────────────────────────────────

    private double dv(Map<String, Object> m, String k) {
        if (m == null) return 0.0;
        Object v = m.get(k);
        return v instanceof Number n ? n.doubleValue() : 0.0;
    }

    private double fmt2(double v) { return Math.round(v * 100.0) / 100.0; }

    private Map<String, Object> fail(String s, String msg) {
        return Map.of("result", "failed", "status", s, "errormessage", msg);
    }

    private Map<String, Object> noData() {
        return Map.of("result", "success", "status", "1004", "errormessage", "검색 결과가 없습니다.");
    }
}
