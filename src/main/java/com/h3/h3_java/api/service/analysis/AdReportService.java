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
public class AdReportService {

    private final AccountMapper accountMapper;
    private final DashboardMongoService mongoService;

    record AdConfig(String dailyCol, String adMasterCol, String agMasterCol, String campMasterCol,
                    String advField, String campIdField, String campNameField,
                    String adMasterIdField, String convtypeCol, boolean isNaver) {}

    private static final Map<String, AdConfig> MD_MAP = Map.of(
        "N",      new AdConfig("naver_ad_daily",      "naver_ad",       "naver_adgroup",      "naver_campaign",
                               "daily_advid", "campaignid", "campaignname", "adid",  "naver_ad_convtype",      true),
        "D",      new AdConfig("kakao_sa_ad_daily",   "kakao_sa_ad",    "kakao_sa_adgroup",   "kakao_sa_campaign",
                               "advkey",      "cid",        "cname",        "aid",   null,                      false),
        "K",      new AdConfig("kakao_mo_ad_daily",   "kakao_mo_ad",    "kakao_mo_adgroup",   "kakao_mo_campaign",
                               "advkey",      "cid",        "cname",        "aid",   null,                      false),
        "Nda",    new AdConfig("naver_gfa_ad_daily",  "naver_gfa_ad",   "naver_gfa_adgroup",  "naver_gfa_campaign",
                               "daily_advid", "cid",        "cname",        "aid",   "naver_gfa_ad_convtype",  false),
        "google", new AdConfig("google_ad_daily",     "google_ad",      "google_adgroup",     "google_campaign",
                               "daily_advid", "cid",        "cname",        "aid",   null,                      false)
    );

    public Map<String, Object> getAdReport(String userId, String md, String fromdate, String todate,
                                             String cfrom, String cto,
                                             String kpi, String sort, int start, int display) {
        AccountDto acc = accountMapper.selectByUserId(userId);
        if (acc == null) return fail("1009", "계정을 확인해 주세요.");

        String comparefrom = (cfrom != null && !cfrom.isBlank()) ? cfrom : fromdate;
        String compareto   = (cto   != null && !cto.isBlank())   ? cto   : todate;

        if (kpi != null && !kpi.isBlank()) {
            return getAdTop(acc, md, fromdate, todate, comparefrom, compareto, kpi);
        } else {
            return getAd(acc, md, fromdate, todate, comparefrom, compareto, sort, start, display);
        }
    }

    // ─── 소재 목록 ────────────────────────────────────────────────────────────

    private Map<String, Object> getAd(AccountDto acc, String md, String from, String to,
                                       String cfrom, String cto, String sort, int start, int display) {
        List<Map<String, Object>> allRows = new ArrayList<>();

        for (String targetMd : getMdList(md)) {
            AdConfig cfg = MD_MAP.get(targetMd);
            if (cfg == null) continue;
            String advid = getAdvid(acc, targetMd);
            if (advid == null || advid.isBlank()) continue;

            List<Map<String, Object>> stats = mongoService.aggregateByAd(advid, from, to, cfg.dailyCol(), cfg.advField());
            if (stats.isEmpty()) continue;

            Map<String, Document> adMasterMap  = buildMasterMap(advid, cfg.adMasterCol(), cfg.adMasterIdField());
            Map<String, Document> agMasterMap   = buildMasterMap(advid, cfg.agMasterCol(), "gid");
            Map<String, Document> campMasterMap = buildMasterMap(advid, cfg.campMasterCol(), cfg.campIdField());
            Map<String, Map<String, Object>> adConvtype = cfg.convtypeCol() != null
                ? mongoService.aggregateConvtypeByAdId(advid, from, to, cfg.convtypeCol())
                : new HashMap<>();

            for (Map<String, Object> stat : stats) {
                allRows.add(buildRow(stat, adMasterMap, agMasterMap, campMasterMap, adConvtype, cfg));
            }
        }

        if (allRows.isEmpty()) return noData();

        sortRows(allRows, sort);

        int total   = allRows.size();
        int fromIdx = start * display;
        int toIdx   = Math.min(fromIdx + display, total);
        List<Map<String, Object>> paged = fromIdx < total ? allRows.subList(fromIdx, toIdx) : Collections.emptyList();

        // 현재 기간 전체 합계
        Map<String, Object> totalMap = buildRowTotal(allRows);

        // 비교 기간 전체 합계
        Map<String, Object> compMap = buildCompTotal(acc, md, cfrom, cto);

        // cp 계산
        totalMap.put("cp", buildCp2(totalMap, compMap));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ads",    paged);
        data.put("topads", "");
        data.put("total",  totalMap);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("result",      "success");
        res.put("status",      "200");
        res.put("data",        data);
        res.put("resultcount", 0);
        res.put("totalcount",  total);
        return res;
    }

    // ─── KPI별 top 10 ────────────────────────────────────────────────────────

    private Map<String, Object> getAdTop(AccountDto acc, String md, String from, String to,
                                          String cfrom, String cto, String kpi) {
        String[] kpis = kpi.split(",");

        // kpi별 독립 row 목록 (KeywordReport 패턴: 각 kpi에 별도 복사본 추가)
        Map<String, List<Map<String, Object>>> allStats = new LinkedHashMap<>();
        for (String k : kpis) allStats.put(k.trim(), new ArrayList<>());

        // 비교기간 ad_id → {raw stats + 파생지표}
        Map<String, Map<String, Object>> compStatsByAdId = new HashMap<>();

        for (String targetMd : getMdList(md)) {
            AdConfig cfg = MD_MAP.get(targetMd);
            if (cfg == null) continue;
            String advid = getAdvid(acc, targetMd);
            if (advid == null || advid.isBlank()) continue;

            List<Map<String, Object>> stats = mongoService.aggregateByAd(advid, from, to, cfg.dailyCol(), cfg.advField());
            if (stats.isEmpty()) continue;

            // 비교기간 stats (파생지표 포함)
            List<Map<String, Object>> compStats = mongoService.aggregateByAd(advid, cfrom, cto, cfg.dailyCol(), cfg.advField());
            for (Map<String, Object> s : compStats) {
                String adid = (String) s.get("ad_id");
                if (adid == null) continue;
                Map<String, Object> enriched = new LinkedHashMap<>(s);
                double im2=toDoubleObj(s.get("im")), clk2=toDoubleObj(s.get("clk")),
                       cst2=toDoubleObj(s.get("cst")), cv2=toDoubleObj(s.get("cv")), cr2=toDoubleObj(s.get("cr"));
                enriched.putAll(calcMetrics(im2, clk2, cst2, cv2, cr2));
                compStatsByAdId.put(adid, enriched);
            }

            Map<String, Document> adMasterMap  = buildMasterMap(advid, cfg.adMasterCol(), cfg.adMasterIdField());
            Map<String, Document> agMasterMap   = buildMasterMap(advid, cfg.agMasterCol(), "gid");
            Map<String, Document> campMasterMap = buildMasterMap(advid, cfg.campMasterCol(), cfg.campIdField());
            Map<String, Map<String, Object>> adConvtype = cfg.convtypeCol() != null
                ? mongoService.aggregateConvtypeByAdId(advid, from, to, cfg.convtypeCol())
                : new HashMap<>();

            for (Map<String, Object> stat : stats) {
                Map<String, Object> row = buildRow(stat, adMasterMap, agMasterMap, campMasterMap, adConvtype, cfg);
                // kpi마다 별도 복사본 추가 (공유 객체 사용 금지)
                for (String k : kpis) allStats.get(k.trim()).add(new LinkedHashMap<>(row));
            }
        }

        Map<String, Object> topads = new LinkedHashMap<>();
        for (String k : kpis) {
            String t = k.trim();
            List<Map<String, Object>> rows = allStats.get(t);

            if (rows.isEmpty()) {
                topads.put(t, Collections.emptyList());
                continue;
            }

            rows.sort((a, b) -> Double.compare(toDoubleObj(b.get(t)), toDoubleObj(a.get(t))));
            List<Map<String, Object>> top10 = rows.size() > 10 ? new ArrayList<>(rows.subList(0, 10)) : rows;

            for (Map<String, Object> row : top10) {
                String adid = (String) row.get("ad_id");
                Map<String, Object> compStat = compStatsByAdId.get(adid);
                double cur  = toDoubleObj(row.get(t));
                double comp = compStat != null ? toDoubleObj(compStat.get(t)) : 0.0;
                Map<String, Object> cpPer = new LinkedHashMap<>();
                cpPer.put(t, (cur > 0 && comp > 0) ? fmt((cur - comp) / comp * 100) : 0);
                Map<String, Object> cpCp = new LinkedHashMap<>();
                cpCp.put(t, Math.round(comp));
                Map<String, Object> cp = new LinkedHashMap<>();
                cp.put("per", cpPer); cp.put("cp", cpCp);
                row.put("cp", cp);
            }
            topads.put(t, top10);
        }

        boolean anyData = allStats.values().stream().anyMatch(l -> !l.isEmpty());
        if (!anyData) {
            Map<String, Object> res = new LinkedHashMap<>();
            res.put("result", "success"); res.put("status", "200");
            res.put("data",   Map.of("ads", "", "topads", topads, "total", ""));
            res.put("resultcount", 0); res.put("totalcount", 0);
            return res;
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("result", "success"); res.put("status", "200");
        res.put("data",  Map.of("ads", "", "topads", topads, "total", ""));
        res.put("resultcount", 0); res.put("totalcount", 0);
        return res;
    }

    // ─── 행 빌드 ─────────────────────────────────────────────────────────────

    private Map<String, Object> buildRow(Map<String, Object> stat,
                                          Map<String, Document> adMasterMap,
                                          Map<String, Document> agMasterMap,
                                          Map<String, Document> campMasterMap,
                                          Map<String, Map<String, Object>> adConvtype,
                                          AdConfig cfg) {
        String adid = (String) stat.get("ad_id");
        String agid = (String) stat.get("adgroup_id");
        String cid  = (String) stat.get("campaign_id");

        Document adM   = adMasterMap.getOrDefault(adid != null ? adid : "", new Document());
        Document agM   = agMasterMap.getOrDefault(agid != null ? agid : "", new Document());
        Document campM = campMasterMap.getOrDefault(cid  != null ? cid  : "", new Document());

        double im=toDouble(stat,"im"), clk=toDouble(stat,"clk"), cst=toDouble(stat,"cst"),
               cv=toDouble(stat,"cv"), cr=toDouble(stat,"cr");

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("campaignid",        cid  != null ? cid  : "");
        row.put("campaign_name",     str(campM, cfg.campNameField()));
        row.put("adgroup_id",        agid != null ? agid : "");
        row.put("adgroup_name",      str(agM, "gname"));
        row.put("ad_id",             adid != null ? adid : "");
        row.put("ad_headline",       getAdField(adM, cfg, "headline"));
        row.put("ad_description",    str(adM, "description"));
        row.put("ad_pc_display",     getAdField(adM, cfg, "pc_url"));
        row.put("ad_pc_final",       getAdField(adM, cfg, "pc_url"));
        row.put("im",  Math.round(im));  row.put("clk", Math.round(clk));
        row.put("cst", Math.round(cst)); row.put("cv",  Math.round(cv)); row.put("cr", Math.round(cr));
        row.putAll(calcMetrics(im, clk, cst, cv, cr));

        double pCv=ctCnt(adConvtype,adid,"purchase"), pCr=ctVal(adConvtype,adid,"purchase");
        double sCv=ctCnt(adConvtype,adid,"sign_up"),  sCr=ctVal(adConvtype,adid,"sign_up");
        double cCv=ctCnt(adConvtype,adid,"add_to_cart"), cCr=ctVal(adConvtype,adid,"add_to_cart");
        double lCv=ctCnt(adConvtype,adid,"lead"),     lCr=ctVal(adConvtype,adid,"lead");
        row.put("purchase_cv", (long)pCv); row.put("purchase_cr", (long)pCr);
        row.put("signup_cv",   (long)sCv); row.put("signup_cr",   (long)sCr);
        row.put("cart_cv",     (long)cCv); row.put("cart_cr",     (long)cCr);
        row.put("lead_cv",     (long)lCv); row.put("lead_cr",     (long)lCr);
        row.put("other_cv",    0L);        row.put("other_cr",    0L);
        row.put("purchase_roas", (pCr > 0 && cst > 0) ? fmt(pCr / cst * 100) : 0);

        row.put("ad_imgurl1", str(adM, "imgurl1"));
        row.put("ad_imgurl2", str(adM, "imgurl2"));
        row.put("ad_imgurl3", str(adM, "imgurl3"));
        Object typeVal = adM.get("type");
        row.put("ad_type", typeVal instanceof Number n ? n.intValue() : "");
        row.put("ad_image_pbase64", "");

        return row;
    }

    private String getAdField(Document ad, AdConfig cfg, String field) {
        return switch (field) {
            case "headline" -> cfg.isNaver() ? str(ad, "subject")      : str(ad, "headline");
            case "pc_url"   -> cfg.isNaver() ? str(ad, "plandingurl")  : str(ad, "purl");
            default -> "";
        };
    }

    // ─── 전체 합계 (현재 기간) ────────────────────────────────────────────────

    private Map<String, Object> buildRowTotal(List<Map<String, Object>> rows) {
        double im=0, clk=0, cst=0, cv=0, cr=0;
        double pCv=0, pCr=0, sCv=0, sCr=0, cCv=0, cCr=0, lCv=0, lCr=0, oCv=0, oCr=0;
        for (Map<String, Object> row : rows) {
            im  += toDoubleObj(row.get("im"));
            clk += toDoubleObj(row.get("clk"));
            cst += toDoubleObj(row.get("cst"));
            cv  += toDoubleObj(row.get("cv"));
            cr  += toDoubleObj(row.get("cr"));
            pCv += toDoubleObj(row.get("purchase_cv")); pCr += toDoubleObj(row.get("purchase_cr"));
            sCv += toDoubleObj(row.get("signup_cv"));   sCr += toDoubleObj(row.get("signup_cr"));
            cCv += toDoubleObj(row.get("cart_cv"));     cCr += toDoubleObj(row.get("cart_cr"));
            lCv += toDoubleObj(row.get("lead_cv"));     lCr += toDoubleObj(row.get("lead_cr"));
            oCv += toDoubleObj(row.get("other_cv"));    oCr += toDoubleObj(row.get("other_cr"));
        }
        return buildTotalMap(im, clk, cst, cv, cr, pCv, pCr, sCv, sCr, cCv, cCr, lCv, lCr, oCv, oCr);
    }

    // ─── 전체 합계 (비교 기간) ────────────────────────────────────────────────

    private Map<String, Object> buildCompTotal(AccountDto acc, String md, String cfrom, String cto) {
        double im=0, clk=0, cst=0, cv=0, cr=0;
        double pCv=0, pCr=0, sCv=0, sCr=0, cCv=0, cCr=0, lCv=0, lCr=0, oCv=0, oCr=0;

        for (String targetMd : getMdList(md)) {
            AdConfig cfg = MD_MAP.get(targetMd);
            if (cfg == null) continue;
            String advid = getAdvid(acc, targetMd);
            if (advid == null || advid.isBlank()) continue;

            List<Map<String, Object>> compStats = mongoService.aggregateByAd(advid, cfrom, cto, cfg.dailyCol(), cfg.advField());
            for (Map<String, Object> s : compStats) {
                im  += toDoubleObj(s.get("im"));
                clk += toDoubleObj(s.get("clk"));
                cst += toDoubleObj(s.get("cst"));
                cv  += toDoubleObj(s.get("cv"));
                cr  += toDoubleObj(s.get("cr"));
            }

            if (cfg.convtypeCol() != null) {
                Map<String, Map<String, Object>> ct = mongoService.aggregateConvtype(advid, cfrom, cto, cfg.convtypeCol());
                pCv += convTotCnt(ct, "purchase");    pCr += convTotVal(ct, "purchase");
                sCv += convTotCnt(ct, "sign_up");     sCr += convTotVal(ct, "sign_up");
                cCv += convTotCnt(ct, "add_to_cart"); cCr += convTotVal(ct, "add_to_cart");
                lCv += convTotCnt(ct, "lead");        lCr += convTotVal(ct, "lead");
                Set<String> main = Set.of("purchase", "sign_up", "add_to_cart", "lead");
                for (Map.Entry<String, Map<String, Object>> e : ct.entrySet()) {
                    if (!main.contains(e.getKey())) {
                        oCv += toDoubleObj(e.getValue().get("cnt"));
                        oCr += toDoubleObj(e.getValue().get("value"));
                    }
                }
            }
        }

        return buildTotalMap(im, clk, cst, cv, cr, pCv, pCr, sCv, sCr, cCv, cCr, lCv, lCr, oCv, oCr);
    }

    private Map<String, Object> buildTotalMap(double im, double clk, double cst, double cv, double cr,
                                               double pCv, double pCr, double sCv, double sCr,
                                               double cCv, double cCr, double lCv, double lCr,
                                               double oCv, double oCr) {
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("im", (long)im); t.put("clk", (long)clk); t.put("cst", (long)cst);
        t.put("cv", (long)cv); t.put("cr", (long)cr);
        t.putAll(calcMetrics(im, clk, cst, cv, cr));
        t.put("purchase_cv", (long)pCv); t.put("purchase_cr", (long)pCr);
        t.put("signup_cv",   (long)sCv); t.put("signup_cr",   (long)sCr);
        t.put("cart_cv",     (long)cCv); t.put("cart_cr",     (long)cCr);
        t.put("lead_cv",     (long)lCv); t.put("lead_cr",     (long)lCr);
        t.put("other_cv",    (long)oCv); t.put("other_cr",    (long)oCr);
        t.put("purchase_roas", (cst > 0 && pCr > 0) ? fmt(pCr / cst * 100) : 0);
        return t;
    }

    // ─── getCp2 상당 (PHP getCp2 구현) ───────────────────────────────────────

    private Map<String, Object> buildCp2(Map<String, Object> cur, Map<String, Object> comp) {
        String[] keys = {"im","clk","cst","cv","cr","ctr","cpc","cpa","cvr","roas",
                         "purchase_cv","purchase_cr","signup_cv","signup_cr",
                         "cart_cv","cart_cr","lead_cv","lead_cr","other_cv","other_cr","purchase_roas"};
        Map<String, Object> per = new LinkedHashMap<>();
        Map<String, Object> cp  = new LinkedHashMap<>();
        for (String k : keys) {
            double a = toDoubleObj(cur.get(k));
            double b = toDoubleObj(comp.get(k));
            if (a > 0 && b > 0) {
                per.put(k, fmt((a - b) / b * 100));
                // compMap 원본 타입 그대로 유지 (Long→integer, Double→float 직렬화 보장)
                Object orig = comp.get(k);
                cp.put(k, orig != null ? orig : b);
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

    // ─── 마스터 조회 ──────────────────────────────────────────────────────────

    private Map<String, Document> buildMasterMap(String advid, String collection, String idField) {
        Map<String, Document> map = new HashMap<>();
        for (Document d : mongoService.findCampaigns(advid, collection)) {
            String id = d.getString(idField);
            if (id != null) map.put(id, d);
        }
        return map;
    }

    // ─── 공통 헬퍼 ───────────────────────────────────────────────────────────

    private List<String> getMdList(String md) {
        if ("TOTAL".equals(md)) return List.of("N", "D", "K", "google");
        if (MD_MAP.containsKey(md)) return List.of(md);
        return List.of("N");
    }

    private String getAdvid(AccountDto acc, String md) {
        return switch (md) {
            case "D"      -> acc.getAccountKakaosa();
            case "K"      -> acc.getAccountKakaomoment();
            case "Nda"    -> acc.getAccountGfa();
            case "google" -> acc.getAccountGoogle();
            default       -> acc.getAccountNaverCustomer();
        };
    }

    private double ctCnt(Map<String, Map<String, Object>> ct, String id, String code) {
        if (id == null) return 0.0;
        Map<String, Object> v = ct.get(id + "|" + code);
        return v != null ? toDoubleObj(v.get("cnt")) : 0.0;
    }

    private double ctVal(Map<String, Map<String, Object>> ct, String id, String code) {
        if (id == null) return 0.0;
        Map<String, Object> v = ct.get(id + "|" + code);
        return v != null ? toDoubleObj(v.get("value")) : 0.0;
    }

    private double convTotCnt(Map<String, Map<String, Object>> ct, String code) {
        Map<String, Object> v = ct.get(code);
        return v != null ? toDoubleObj(v.get("cnt")) : 0.0;
    }

    private double convTotVal(Map<String, Map<String, Object>> ct, String code) {
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

    private String str(Document d, String key) {
        if (d == null) return "";
        String v = d.getString(key);
        return v != null ? v : "";
    }

    private double fmt(double v)         { return Math.round(v * 100.0) / 100.0; }
    private double toDouble(Map<String, Object> m, String k) { Object v=m.get(k); return v instanceof Number n ? n.doubleValue() : 0.0; }
    private double toDoubleObj(Object v) { return v instanceof Number n ? n.doubleValue() : 0.0; }
    private Map<String, Object> fail(String status, String msg) { return Map.of("result","failed","status",status,"errormessage",msg); }
    private Map<String, Object> noData() { return Map.of("result","success","status","1004","errormessage","검색 결과가 없습니다."); }
}
