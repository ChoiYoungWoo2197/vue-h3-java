package com.h3.h3_java.api.service.analysis;

import com.h3.h3_java.api.dto.AccountDto;
import com.h3.h3_java.api.mapper.AccountMapper;
import com.h3.h3_java.raw.mongo.DashboardMongoService;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class KeywordReportService {

    private final AccountMapper accountMapper;
    private final DashboardMongoService mongoService;

    record KwConfig(String dailyCol, String kwMasterCol, String agMasterCol, String campMasterCol,
                    String advField, String campIdField, String campNameField,
                    boolean kwordInDaily, String convtypeCol) {}

    private static final Map<String, KwConfig> MD_MAP = Map.of(
        "N", new KwConfig("naver_keyword_daily",    "naver_keyword",    "naver_adgroup",    "naver_campaign",
                          "daily_advid", "campaignid", "campaignname", false, "naver_keyword_convtype"),
        "D", new KwConfig("kakao_sa_keyword_daily", "kakao_sa_keyword", "kakao_sa_adgroup", "kakao_sa_campaign",
                          "advkey",      "cid",        "cname",         true,  null)
    );

    public Map<String, Object> getKeywordReport(String userId, String md, String fromdate, String todate,
                                                  String cfrom, String cto,
                                                  String kpi, String sort, int start, int display) {
        AccountDto acc = accountMapper.selectByUserId(userId);
        if (acc == null) return fail("1009", "계정을 확인해 주세요.");

        String comparefrom = (cfrom != null && !cfrom.isBlank()) ? cfrom : fromdate;
        String compareto   = (cto   != null && !cto.isBlank())   ? cto   : todate;

        if (kpi != null && !kpi.isBlank()) {
            return getKeywordTop(acc, md, fromdate, todate, kpi);
        } else {
            return getKeyword(acc, md, fromdate, todate, comparefrom, compareto, sort, start, display);
        }
    }

    // ─── 일반 목록 ────────────────────────────────────────────────────────────

    private Map<String, Object> getKeyword(AccountDto acc, String md, String from, String to,
                                            String cfrom, String cto,
                                            String sort, int start, int display) {
        List<Map<String, Object>> allRows = new ArrayList<>();

        // 전체 합산용 누적 변수
        double tIm=0,tClk=0,tCst=0,tCv=0,tCr=0;
        double cIm=0,cClk=0,cCst=0,cCv=0,cCr=0;
        Map<String, Map<String, Object>> totalConvtype  = new HashMap<>();
        Map<String, Map<String, Object>> compareConvtype = new HashMap<>();

        for (String targetMd : getMdList(md)) {
            KwConfig cfg = MD_MAP.get(targetMd);
            if (cfg == null) continue;
            String advid = getAdvid(acc, targetMd);
            if (advid == null || advid.isBlank()) continue;

            // 통계
            List<Map<String, Object>> stats = mongoService.aggregateByKeyword(advid, from, to, cfg.dailyCol(), cfg.advField());
            if (stats.isEmpty()) continue;

            // 비교기간 통계
            List<Map<String, Object>> compStats = mongoService.aggregateByKeyword(advid, cfrom, cto, cfg.dailyCol(), cfg.advField());

            // 마스터
            Map<String, Map<String, Object>> kwMasterMap = buildKwMasterMap(advid, cfg, stats);
            Map<String, String> agNameMap   = buildNameMap(advid, cfg.agMasterCol(), "gid", "gname");
            Map<String, String> campNameMap = buildNameMap(advid, cfg.campMasterCol(), cfg.campIdField(), cfg.campNameField());

            // 전환유형
            Map<String, Map<String, Object>> kwConvtype = new HashMap<>();
            if (cfg.convtypeCol() != null) {
                kwConvtype = mongoService.aggregateConvtypeByKeywordId(advid, from, to, cfg.convtypeCol());
                totalConvtype  = mongoService.aggregateConvtype(advid, from,  to,   cfg.convtypeCol());
                compareConvtype = mongoService.aggregateConvtype(advid, cfrom, cto,  cfg.convtypeCol());
            }

            for (Map<String, Object> stat : stats) {
                double im=toDouble(stat,"im"), clk=toDouble(stat,"clk"), cst=toDouble(stat,"cst"),
                       cv=toDouble(stat,"cv"), cr=toDouble(stat,"cr");
                tIm+=im; tClk+=clk; tCst+=cst; tCv+=cv; tCr+=cr;
                allRows.add(buildRow(stat, kwMasterMap, agNameMap, campNameMap, kwConvtype, false));
            }
            for (Map<String, Object> s : compStats) {
                cIm+=toDouble(s,"im"); cClk+=toDouble(s,"clk"); cCst+=toDouble(s,"cst");
                cCv+=toDouble(s,"cv"); cCr+=toDouble(s,"cr");
            }
        }

        if (allRows.isEmpty()) return noData();

        sortRows(allRows, sort);

        int total   = allRows.size();
        int fromIdx = start * display;
        int toIdx   = Math.min(fromIdx + display, total);
        List<Map<String, Object>> paged = fromIdx < total ? allRows.subList(fromIdx, toIdx) : Collections.emptyList();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("keywords", paged);
        data.put("kpis",     "");
        data.put("total",    buildTotal(tIm,tClk,tCst,tCv,tCr, cIm,cClk,cCst,cCv,cCr, totalConvtype, compareConvtype));

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("result", "success"); res.put("status", "200");
        res.put("data", data);
        res.put("resultcount", 0);
        res.put("totalcount", total);
        return res;
    }

    // ─── KPI 상위 10개 ────────────────────────────────────────────────────────

    private Map<String, Object> getKeywordTop(AccountDto acc, String md, String from, String to, String kpi) {
        String[] kpis = kpi.split(",");
        Map<String, List<Map<String, Object>>> allStats = new LinkedHashMap<>();
        for (String k : kpis) allStats.put(k.trim(), new ArrayList<>());

        for (String targetMd : getMdList(md)) {
            KwConfig cfg = MD_MAP.get(targetMd);
            if (cfg == null) continue;
            String advid = getAdvid(acc, targetMd);
            if (advid == null || advid.isBlank()) continue;

            List<Map<String, Object>> stats = mongoService.aggregateByKeyword(advid, from, to, cfg.dailyCol(), cfg.advField());
            if (stats.isEmpty()) continue;

            Map<String, Map<String, Object>> kwMasterMap = buildKwMasterMap(advid, cfg, stats);
            Map<String, String> agNameMap   = buildNameMap(advid, cfg.agMasterCol(), "gid", "gname");
            Map<String, String> campNameMap = buildNameMap(advid, cfg.campMasterCol(), cfg.campIdField(), cfg.campNameField());
            Map<String, Map<String, Object>> kwConvtype = cfg.convtypeCol() != null
                ? mongoService.aggregateConvtypeByKeywordId(advid, from, to, cfg.convtypeCol())
                : new HashMap<>();

            for (Map<String, Object> stat : stats) {
                Map<String, Object> row = buildRow(stat, kwMasterMap, agNameMap, campNameMap, kwConvtype, true);
                for (String k : kpis) allStats.get(k.trim()).add(row);
            }
        }

        Map<String, Object> kpisResult = new LinkedHashMap<>();
        for (String k : kpis) {
            String t = k.trim();
            List<Map<String, Object>> rows = allStats.get(t);
            rows.sort((a, b) -> {
                Object oa = a.get(t), ob = b.get(t);
                double da = oa instanceof Number n ? n.doubleValue() : 0.0;
                double db = ob instanceof Number n ? n.doubleValue() : 0.0;
                return Double.compare(db, da);
            });
            kpisResult.put(t, rows.size() > 10 ? new ArrayList<>(rows.subList(0, 10)) : rows);
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("result", "success"); res.put("status", "200");
        res.put("data", Map.of("kpis", kpisResult));
        res.put("resultcount", 0); res.put("totalcount", 0);
        return res;
    }

    // ─── 행 빌드 ─────────────────────────────────────────────────────────────

    private Map<String, Object> buildRow(Map<String, Object> stat,
                                          Map<String, Map<String, Object>> kwMasterMap,
                                          Map<String, String> agNameMap,
                                          Map<String, String> campNameMap,
                                          Map<String, Map<String, Object>> kwConvtype,
                                          boolean includeIds) {
        String kwid = (String) stat.get("keyword_id");
        String agid = (String) stat.get("adgroup_id");
        String cid  = (String) stat.get("campaign_id");

        Map<String, Object> kwMaster = kwMasterMap.getOrDefault(kwid != null ? kwid : "", Collections.emptyMap());

        double im=toDouble(stat,"im"), clk=toDouble(stat,"clk"), cst=toDouble(stat,"cst"),
               cv=toDouble(stat,"cv"), cr=toDouble(stat,"cr");

        Map<String, Object> row = new LinkedHashMap<>();
        if (includeIds) {
            row.put("campaign_id", cid  != null ? cid  : "");
            row.put("adgroup_id",  agid != null ? agid : "");
        }
        row.put("campaignid",        cid  != null ? cid  : "");
        row.put("campaign_name",     campNameMap.getOrDefault(cid,  ""));
        row.put("adgroup_id",        agid != null ? agid : "");
        row.put("adgroup_name",      agNameMap.getOrDefault(agid,  ""));
        row.put("keyword_id",        kwid != null ? kwid : "");
        row.put("keyword_name",      kwMaster.getOrDefault("kword", ""));
        row.put("keyword_status",    0);
        row.put("keyword_qigrade",   kwMaster.getOrDefault("qigrade", 0));
        row.put("keyword_bidamount", kwMaster.getOrDefault("bidamount", 0));
        row.put("im",  Math.round(im));  row.put("clk", Math.round(clk));
        row.put("cst", Math.round(cst)); row.put("cv",  Math.round(cv)); row.put("cr", Math.round(cr));
        row.putAll(calcMetrics(im, clk, cst, cv, cr));

        // 전환유형
        double pCv = ctCnt(kwConvtype, kwid, "purchase"), pCr = ctVal(kwConvtype, kwid, "purchase");
        row.put("purchase_cv", (long) pCv); row.put("purchase_cr", (long) pCr);
        row.put("signup_cv",   (long) ctCnt(kwConvtype, kwid, "sign_up"));
        row.put("signup_cr",   (long) ctVal(kwConvtype, kwid, "sign_up"));
        row.put("cart_cv",     (long) ctCnt(kwConvtype, kwid, "add_to_cart"));
        row.put("cart_cr",     (long) ctVal(kwConvtype, kwid, "add_to_cart"));
        row.put("lead_cv",     (long) ctCnt(kwConvtype, kwid, "lead"));
        row.put("lead_cr",     (long) ctVal(kwConvtype, kwid, "lead"));
        row.put("other_cv", 0L); row.put("other_cr", 0L);
        row.put("purchase_roas", (pCr > 0 && cst > 0) ? fmt(pCr / cst * 100) : 0);

        return row;
    }

    // ─── data.total 빌드 ─────────────────────────────────────────────────────

    private Map<String, Object> buildTotal(double tIm, double tClk, double tCst, double tCv, double tCr,
                                            double cIm, double cClk, double cCst, double cCv, double cCr,
                                            Map<String, Map<String, Object>> totalCt,
                                            Map<String, Map<String, Object>> compareCt) {
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("im", Math.round(tIm)); t.put("clk", Math.round(tClk));
        t.put("cst", Math.round(tCst)); t.put("cv", Math.round(tCv)); t.put("cr", Math.round(tCr));
        t.putAll(calcMetrics(tIm, tClk, tCst, tCv, tCr));

        // 전환유형 합산
        double tPcv=ctCnt2(totalCt,"purchase"), tPcr=ctVal2(totalCt,"purchase");
        double tScv=ctCnt2(totalCt,"sign_up"),  tScr=ctVal2(totalCt,"sign_up");
        double tCacv=ctCnt2(totalCt,"add_to_cart"), tCacr=ctVal2(totalCt,"add_to_cart");
        double tLcv=ctCnt2(totalCt,"lead"),     tLcr=ctVal2(totalCt,"lead");
        t.put("purchase_cv",(long)tPcv); t.put("purchase_cr",(long)tPcr);
        t.put("signup_cv",(long)tScv);   t.put("signup_cr",(long)tScr);
        t.put("cart_cv",(long)tCacv);    t.put("cart_cr",(long)tCacr);
        t.put("lead_cv",(long)tLcv);     t.put("lead_cr",(long)tLcr);
        t.put("other_cv",0L); t.put("other_cr",0L);
        t.put("purchase_roas", (tPcr>0 && tCst>0) ? fmt(tPcr/tCst*100) : 0);

        // 비교기간 cp
        double cPcv=ctCnt2(compareCt,"purchase"), cPcr=ctVal2(compareCt,"purchase");
        double cScv=ctCnt2(compareCt,"sign_up"),  cScr=ctVal2(compareCt,"sign_up");
        double cCacv=ctCnt2(compareCt,"add_to_cart"), cCacr=ctVal2(compareCt,"add_to_cart");
        double cLcv=ctCnt2(compareCt,"lead"),     cLcr=ctVal2(compareCt,"lead");

        Map<String, Object> cpCp = new LinkedHashMap<>();
        cpCp.put("im", Math.round(cIm)); cpCp.put("clk", Math.round(cClk));
        cpCp.put("cst", Math.round(cCst)); cpCp.put("cv", Math.round(cCv)); cpCp.put("cr", Math.round(cCr));
        cpCp.putAll(calcMetrics(cIm, cClk, cCst, cCv, cCr));
        cpCp.put("purchase_cv",(long)cPcv); cpCp.put("purchase_cr",(long)cPcr);
        cpCp.put("signup_cv",(long)cScv);   cpCp.put("signup_cr",(long)cScr);
        cpCp.put("cart_cv",(long)cCacv);    cpCp.put("cart_cr",(long)cCacr);
        cpCp.put("lead_cv",(long)cLcv);     cpCp.put("lead_cr",(long)cLcr);
        cpCp.put("other_cv",0L); cpCp.put("other_cr",0L);
        cpCp.put("purchase_roas", (cPcr>0 && cCst>0) ? fmt(cPcr/cCst*100) : 0);

        Map<String, Object> cpPer = new LinkedHashMap<>();
        String[] keys = {"im","clk","cst","cv","cr","ctr","cpc","cpa","cvr","roas",
                         "purchase_cv","purchase_cr","signup_cv","signup_cr","cart_cv","cart_cr",
                         "lead_cv","lead_cr","other_cv","other_cr","purchase_roas"};
        for (String k : keys) {
            double cur = toDoubleObj(t.get(k)), comp = toDoubleObj(cpCp.get(k));
            cpPer.put(k, (cur > 0 && comp > 0) ? fmt((cur - comp) / comp * 100) : 0);
        }

        Map<String, Object> cp = new LinkedHashMap<>();
        cp.put("per", cpPer); cp.put("cp", cpCp);
        t.put("cp", cp);
        return t;
    }

    // ─── 마스터 조회 ──────────────────────────────────────────────────────────

    private Map<String, Map<String, Object>> buildKwMasterMap(String advid, KwConfig cfg, List<Map<String, Object>> stats) {
        Map<String, Map<String, Object>> map = new HashMap<>();
        if (cfg.kwordInDaily()) {
            for (Map<String, Object> s : stats) {
                String kwid = (String) s.get("keyword_id");
                if (kwid != null) map.put(kwid, Map.of("kword", s.getOrDefault("kname", ""), "bidamount", 0, "qigrade", 0));
            }
        } else {
            for (Document d : mongoService.findCampaigns(advid, cfg.kwMasterCol())) {
                String kwid = d.getString("kwid");
                if (kwid != null) map.put(kwid, Map.of(
                    "kword",     d.getString("kword")   != null ? d.getString("kword") : "",
                    "bidamount", d.getInteger("bidamount", 0),
                    "qigrade",   d.getInteger("qigrade",   0)
                ));
            }
        }
        return map;
    }

    private Map<String, String> buildNameMap(String advid, String collection, String idField, String nameField) {
        Map<String, String> map = new HashMap<>();
        for (Document d : mongoService.findCampaigns(advid, collection)) {
            String id = d.getString(idField), name = d.getString(nameField);
            if (id != null) map.put(id, name != null ? name : "");
        }
        return map;
    }

    // ─── 공통 헬퍼 ───────────────────────────────────────────────────────────

    private List<String> getMdList(String md) {
        if ("TOTAL".equals(md)) return List.of("N", "D");
        if (MD_MAP.containsKey(md)) return List.of(md);
        return List.of("N");
    }

    private String getAdvid(AccountDto acc, String md) {
        return "D".equals(md) ? acc.getAccountKakaosa() : acc.getAccountNaverCustomer();
    }

    private double ctCnt(Map<String, Map<String, Object>> ct, String kwid, String code) {
        if (kwid == null) return 0.0;
        Map<String, Object> v = ct.get(kwid + "|" + code);
        return v != null ? toDoubleObj(v.get("cnt")) : 0.0;
    }

    private double ctVal(Map<String, Map<String, Object>> ct, String kwid, String code) {
        if (kwid == null) return 0.0;
        Map<String, Object> v = ct.get(kwid + "|" + code);
        return v != null ? toDoubleObj(v.get("value")) : 0.0;
    }

    private double ctCnt2(Map<String, Map<String, Object>> ct, String code) {
        Map<String, Object> v = ct.get(code);
        return v != null ? toDoubleObj(v.get("cnt")) : 0.0;
    }

    private double ctVal2(Map<String, Map<String, Object>> ct, String code) {
        Map<String, Object> v = ct.get(code);
        return v != null ? toDoubleObj(v.get("value")) : 0.0;
    }

    private void sortRows(List<Map<String, Object>> rows, String sort) {
        String s = sort != null ? sort.trim() : "";
        String field; boolean desc;
        if (s.length() >= 2) { field = s.substring(0, s.length()-1); desc = s.charAt(s.length()-1) == 'd'; }
        else                  { field = "cst"; desc = true; }
        String f = field;
        rows.sort((x, y) -> {
            double dx = toDoubleObj(x.get(f)), dy = toDoubleObj(y.get(f));
            return desc ? Double.compare(dy, dx) : Double.compare(dx, dy);
        });
    }

    private Map<String, Object> calcMetrics(double im, double clk, double cst, double cv, double cr) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ctr",  (im>0&&clk>0)  ? fmt(clk/im*100)  : 0);
        m.put("cpc",  (cst>0&&clk>0) ? fmt(cst/clk)     : 0);
        m.put("cpa",  (cst>0&&cv>0)  ? fmt(cst/cv)      : 0);
        m.put("cvr",  (cv>0&&clk>0)  ? fmt(cv/clk*100)  : 0);
        m.put("roas", (cr>0&&cst>0)  ? fmt(cr/cst*100)  : 0);
        return m;
    }

    private double fmt(double v)           { return Math.round(v * 100.0) / 100.0; }
    private double toDouble(Map<String, Object> m, String k) { Object v=m.get(k); return v instanceof Number n ? n.doubleValue() : 0.0; }
    private double toDoubleObj(Object v)   { return v instanceof Number n ? n.doubleValue() : 0.0; }
    private Map<String, Object> fail(String status, String msg) { return Map.of("result","failed","status",status,"errormessage",msg); }
    private Map<String, Object> noData()   { return Map.of("result","success","status","1004","errormessage","검색 결과가 없습니다."); }
}
