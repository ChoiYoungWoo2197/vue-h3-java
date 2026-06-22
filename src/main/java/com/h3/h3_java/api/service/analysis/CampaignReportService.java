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
public class CampaignReportService {

    private final AccountMapper accountMapper;
    private final DashboardMongoService mongoService;

    // 네이버 SA 캠페인 타입
    private static final Map<String, Integer> NAVER_CAMP_TYPE = Map.of(
        "web_site", 1, "shopping", 2, "power_contents", 3, "brand_search", 4, "place", 6
    );
    // 네이버 GFA 캠페인 타입
    private static final Map<String, Integer> GFA_CAMP_TYPE = Map.of(
        "conversion", 0, "web_site_traffic", 1, "install_app", 2, "watch_video", 3,
        "catalog", 4, "shopping", 5, "lead", 6, "pmax", 7
    );
    // 카카오 MO 캠페인 타입
    private static final Map<String, Integer> KAKAOMO_CAMP_TYPE = Map.of(
        "talk_biz_board", 0, "display", 1, "talk_channel", 2,
        "daum_shopping", 3, "video", 4, "sponsored_board", 5
    );
    // 구글 캠페인 타입
    private static final Map<String, Integer> GOOGLE_CAMP_TYPE = Map.ofEntries(
        Map.entry("demand_gen", 1), Map.entry("display", 2), Map.entry("hotel", 3),
        Map.entry("local", 4), Map.entry("local_services", 5), Map.entry("multi_channel", 6),
        Map.entry("performance_max", 7), Map.entry("search", 8), Map.entry("shopping", 9),
        Map.entry("smart", 10), Map.entry("travel", 11), Map.entry("video", 12),
        Map.entry("unknown", 98), Map.entry("unspecified", 99)
    );

    public Map<String, Object> getCampaignReport(String userId, String fromdate, String todate,
                                                  String comparefromdate, String comparetodate) {
        AccountDto acc = accountMapper.selectByUserId(userId);
        if (acc == null) return Map.of("result", "failed", "status", "1009", "errormessage", "계정 없음");

        String cfrom = (comparefromdate != null && !comparefromdate.isEmpty()) ? comparefromdate : fromdate;
        String cto   = (comparetodate  != null && !comparetodate.isEmpty())  ? comparetodate  : todate;

        Map<String, Object> media = new LinkedHashMap<>();
        media.put("naver",   buildNaverReport(acc.getAccountNaverCustomer(), fromdate, todate, cfrom, cto));
        media.put("kakaosa", buildKakaoSaReport(acc.getAccountKakaosa(),     fromdate, todate, cfrom, cto));
        media.put("kakaomo", buildKakaoMoReport(acc.getAccountKakaomoment(), fromdate, todate, cfrom, cto));
        media.put("naverda", buildGfaReport(acc.getAccountGfa(),             fromdate, todate, cfrom, cto));
        media.put("google",  buildGoogleReport(acc.getAccountGoogle(),       fromdate, todate, cfrom, cto));

        return Map.of("result", "success", "status", "200", "data", Map.of("media", media));
    }

    // ─── 네이버 SA ────────────────────────────────────────────────────────────

    private Map<String, Object> buildNaverReport(String advid, String from, String to, String cfrom, String cto) {
        if (advid == null || advid.isBlank()) return emptyReport();

        List<Map<String, Object>> graph     = buildGraph(advid, from, to, "naver_campaign_daily", false);
        Map<String, Object>       compTotal = aggregateTotalFull(advid, cfrom, cto, "naver_campaign_daily", false, "naver_campaign_convtype");
        Map<String, Object>       curTotal  = aggregateTotalFull(advid, from, to, "naver_campaign_daily", false, "naver_campaign_convtype");

        // 캠페인별 집계 + 마스터 JOIN
        List<Map<String, Object>> campStats  = mongoService.aggregateByCampaign(advid, from, to, "naver_campaign_daily", "daily_advid");
        List<Document>            masters    = mongoService.findNaverCampaigns(advid);
        Map<String, Document>     masterMap  = new HashMap<>();
        for (Document d : masters) masterMap.put(d.getString("campaignid"), d);

        // 전환유형 캠페인별
        Map<String, Map<String, Object>> convtype = mongoService.aggregateConvtypeByCampaignId(advid, from, to, "naver_campaign_convtype");

        // 캠페인 타입별 그룹핑
        Map<String, List<Map<String, Object>>> groups = new LinkedHashMap<>();
        NAVER_CAMP_TYPE.keySet().forEach(t -> groups.put(t, new ArrayList<>()));

        for (Map<String, Object> cs : campStats) {
            String cid = (String) cs.get("campaign_id");
            Document master = masterMap.get(cid);
            if (master == null) continue;
            int typeCode = getInt(master, "campaigntype", 0);
            String typeName = typeCodeToName(typeCode, NAVER_CAMP_TYPE);
            if (typeName == null) continue;

            double cst = toDouble(cs, "cst");
            Map<String, Object> row = buildCampaignRow(cid, master.getString("campaignname"), typeName,
                getInt(master, "onoff", 0), toDouble(cs, "im"), toDouble(cs, "clk"),
                cst, toDouble(cs, "cv"), toDouble(cs, "cr"), convtype, true);
            groups.get(typeName).add(row);
        }

        Map<String, Object> totals = new LinkedHashMap<>();
        groups.forEach((t, list) -> totals.put(t, getTotalForType(list)));

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("day",   Map.of("graph", graph));
        report.put("cp",    buildCp(curTotal, compTotal));
        report.put("group", groups);
        report.put("total", totals);
        return report;
    }

    // ─── 카카오 SA ────────────────────────────────────────────────────────────

    private Map<String, Object> buildKakaoSaReport(String advid, String from, String to, String cfrom, String cto) {
        if (advid == null || advid.isBlank()) return emptyReport();

        List<Map<String, Object>> graph     = buildGraph(advid, from, to, "kakao_sa_campaign_daily", false);
        Map<String, Object>       compTotal = aggregateTotal(advid, cfrom, cto, "kakao_sa_campaign_daily", false);
        Map<String, Object>       curTotal  = sumGraph(graph);

        List<Map<String, Object>> campStats = mongoService.aggregateByCampaign(advid, from, to, "kakao_sa_campaign_daily", "advkey");
        List<Document>            masters   = mongoService.findCampaigns(advid, "kakao_sa_campaign");
        Map<String, Document>     masterMap = toMasterMap(masters, "cid");

        List<Map<String, Object>> noneList = new ArrayList<>();
        for (Map<String, Object> cs : campStats) {
            String cid = (String) cs.get("campaign_id");
            Document master = masterMap.get(cid);
            String name = master != null ? master.getString("cname") : cid;
            int onoff   = master != null ? getInt(master, "onoff", 0) : 0;
            noneList.add(buildCampaignRow(cid, name, "none", onoff,
                toDouble(cs, "im"), toDouble(cs, "clk"), toDouble(cs, "cst"),
                toDouble(cs, "cv"), toDouble(cs, "cr"), null, false));
        }

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("day",   Map.of("graph", graph));
        report.put("cp",    buildCp(curTotal, compTotal));
        report.put("group", Map.of("none", noneList));
        report.put("total", Map.of("none", getTotalForType(noneList)));
        return report;
    }

    // ─── 카카오 MO ────────────────────────────────────────────────────────────

    private Map<String, Object> buildKakaoMoReport(String advid, String from, String to, String cfrom, String cto) {
        if (advid == null || advid.isBlank()) return emptyReport();

        List<Map<String, Object>> graph     = buildGraph(advid, from, to, "kakao_mo_campaign_daily", false);
        Map<String, Object>       compTotal = aggregateTotal(advid, cfrom, cto, "kakao_mo_campaign_daily", false);
        Map<String, Object>       curTotal  = sumGraph(graph);

        List<Map<String, Object>> campStats = mongoService.aggregateByCampaign(advid, from, to, "kakao_mo_campaign_daily", "advkey");
        List<Document>            masters   = mongoService.findCampaigns(advid, "kakao_mo_campaign");
        Map<String, Document>     masterMap = toMasterMap(masters, "cid");

        Map<String, List<Map<String, Object>>> groups = new LinkedHashMap<>();
        KAKAOMO_CAMP_TYPE.keySet().forEach(t -> groups.put(t, new ArrayList<>()));

        for (Map<String, Object> cs : campStats) {
            String cid    = (String) cs.get("campaign_id");
            Document master = masterMap.get(cid);
            if (master == null) continue;
            String typeName = master.getString("type");
            if (typeName == null || !groups.containsKey(typeName)) continue;
            String name = master.getString("cname");
            int onoff   = getInt(master, "onoff", 0);
            groups.get(typeName).add(buildCampaignRow(cid, name, typeName, onoff,
                toDouble(cs, "im"), toDouble(cs, "clk"), toDouble(cs, "cst"),
                toDouble(cs, "cv"), toDouble(cs, "cr"), null, false));
        }

        Map<String, Object> totals = new LinkedHashMap<>();
        groups.forEach((t, list) -> totals.put(t, getTotalForType(list)));

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("day",   Map.of("graph", graph));
        report.put("cp",    buildCp(curTotal, compTotal));
        report.put("group", groups);
        report.put("total", totals);
        return report;
    }

    // ─── 네이버 GFA ───────────────────────────────────────────────────────────

    private Map<String, Object> buildGfaReport(String advid, String from, String to, String cfrom, String cto) {
        if (advid == null || advid.isBlank()) return emptyReport();

        List<Map<String, Object>> graph     = buildGraph(advid, from, to, "naver_gfa_campaign_daily", false);
        Map<String, Object>       compTotal = aggregateTotalFull(advid, cfrom, cto, "naver_gfa_campaign_daily", false, "naver_gfa_campaign_convtype");
        Map<String, Object>       curTotal  = aggregateTotalFull(advid, from, to, "naver_gfa_campaign_daily", false, "naver_gfa_campaign_convtype");

        List<Map<String, Object>> campStats = mongoService.aggregateByCampaign(advid, from, to, "naver_gfa_campaign_daily", "daily_advid");
        List<Document>            masters   = mongoService.findCampaigns(advid, "naver_gfa_campaign");
        Map<String, Document>     masterMap = toMasterMap(masters, "cid");

        Map<String, Map<String, Object>> convtype = mongoService.aggregateConvtypeByCampaignId(advid, from, to, "naver_gfa_campaign_convtype");

        Map<String, List<Map<String, Object>>> groups = new LinkedHashMap<>();
        GFA_CAMP_TYPE.keySet().forEach(t -> groups.put(t, new ArrayList<>()));

        for (Map<String, Object> cs : campStats) {
            String cid    = (String) cs.get("campaign_id");
            Document master = masterMap.get(cid);
            if (master == null) continue;
            int typeCode  = getInt(master, "type", -1);
            String typeName = typeCodeToName(typeCode, GFA_CAMP_TYPE);
            if (typeName == null) continue;
            double cst = toDouble(cs, "cst");
            groups.get(typeName).add(buildCampaignRow(cid, master.getString("cname"), typeName,
                getInt(master, "onoff", 0), toDouble(cs, "im"), toDouble(cs, "clk"),
                cst, toDouble(cs, "cv"), toDouble(cs, "cr"), convtype, true));
        }

        Map<String, Object> totals = new LinkedHashMap<>();
        groups.forEach((t, list) -> totals.put(t, getTotalForType(list)));

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("day",   Map.of("graph", graph));
        report.put("cp",    buildCp(curTotal, compTotal));
        report.put("group", groups);
        report.put("total", totals);
        return report;
    }

    // ─── 구글 ─────────────────────────────────────────────────────────────────

    private Map<String, Object> buildGoogleReport(String advid, String from, String to, String cfrom, String cto) {
        if (advid == null || advid.isBlank()) return emptyReport();

        List<Map<String, Object>> graph     = buildGraph(advid, from, to, "google_campaign_daily", false);
        Map<String, Object>       compTotal = aggregateTotal(advid, cfrom, cto, "google_campaign_daily", false);
        Map<String, Object>       curTotal  = sumGraph(graph);

        List<Map<String, Object>> campStats = mongoService.aggregateByCampaign(advid, from, to, "google_campaign_daily", "daily_advid");
        List<Document>            masters   = mongoService.findCampaigns(advid, "google_campaign");
        Map<String, Document>     masterMap = toMasterMap(masters, "cid");

        Map<String, List<Map<String, Object>>> groups = new LinkedHashMap<>();
        GOOGLE_CAMP_TYPE.keySet().forEach(t -> groups.put(t, new ArrayList<>()));

        for (Map<String, Object> cs : campStats) {
            String cid    = (String) cs.get("campaign_id");
            Document master = masterMap.get(cid);
            if (master == null) continue;
            int typeCode  = getInt(master, "type", -1);
            String typeName = typeCodeToName(typeCode, GOOGLE_CAMP_TYPE);
            if (typeName == null) typeName = "unknown";
            String name = master.getString("cname");
            int onoff   = getInt(master, "onoff", 0);
            double cst  = Math.round(toDouble(cs, "cst"));
            double cv   = Math.round(toDouble(cs, "cv") * 100.0) / 100.0;
            double cr   = Math.round(toDouble(cs, "cr") * 100.0) / 100.0;
            if (groups.containsKey(typeName))
                groups.get(typeName).add(buildCampaignRow(cid, name, typeName, onoff,
                    toDouble(cs, "im"), toDouble(cs, "clk"), cst, cv, cr, null, false));
        }

        Map<String, Object> totals = new LinkedHashMap<>();
        groups.forEach((t, list) -> totals.put(t, getTotalForType(list)));

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("day",   Map.of("graph", graph));
        report.put("cp",    buildCp(curTotal, compTotal));
        report.put("group", groups);
        report.put("total", totals);
        return report;
    }

    // ─── 공통 헬퍼 ───────────────────────────────────────────────────────────

    private List<Map<String, Object>> buildGraph(String advid, String from, String to,
                                                  String collection, boolean vatNaver) {
        Map<String, Map<String, Object>> byDate = mongoService.aggregateByDate(advid, from, to, collection);
        List<Map<String, Object>> graph = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> e : byDate.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("daily_dt", e.getKey());
            double cst = vatNaver ? toDouble(e.getValue(), "cst") * 1.1 : toDouble(e.getValue(), "cst");
            row.put("im",  Math.round(toDouble(e.getValue(), "im")));
            row.put("clk", Math.round(toDouble(e.getValue(), "clk")));
            row.put("cst", Math.round(cst));
            row.put("cv",  Math.round(toDouble(e.getValue(), "cv")));
            row.put("cr",  Math.round(toDouble(e.getValue(), "cr")));
            row.putAll(calcMetrics(toDouble(e.getValue(), "im"), toDouble(e.getValue(), "clk"),
                cst, toDouble(e.getValue(), "cv"), toDouble(e.getValue(), "cr")));
            graph.add(row);
        }
        return graph;
    }

    private Map<String, Object> aggregateTotal(String advid, String from, String to,
                                                String collection, boolean vatNaver) {
        Map<String, Object> r = mongoService.aggregateTotal(advid, from, to, collection);
        double cst = vatNaver ? toDouble(r, "cst") * 1.1 : toDouble(r, "cst");
        return Map.of("im", toDouble(r, "im"), "clk", toDouble(r, "clk"), "cst", cst,
                      "cv", toDouble(r, "cv"), "cr", toDouble(r, "cr"));
    }

    private Map<String, Object> aggregateTotalFull(String advid, String from, String to,
                                                    String collection, boolean vatNaver, String convCollection) {
        Map<String, Object> r = mongoService.aggregateTotal(advid, from, to, collection);
        double im = toDouble(r, "im"), clk = toDouble(r, "clk"), cv = toDouble(r, "cv"), cr = toDouble(r, "cr");
        double cst = vatNaver ? toDouble(r, "cst") * 1.1 : toDouble(r, "cst");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("im", Math.round(im)); result.put("clk", Math.round(clk));
        result.put("cst", Math.round(cst)); result.put("cv", Math.round(cv)); result.put("cr", Math.round(cr));
        result.putAll(calcMetrics(im, clk, cst, cv, cr));
        if (convCollection != null) {
            Map<String, Map<String, Object>> ct = mongoService.aggregateConvtype(advid, from, to, convCollection);
            double pCv = ctCnt2(ct,"purchase"), pCr = ctVal2(ct,"purchase");
            double sCv = ctCnt2(ct,"sign_up"),  sCr = ctVal2(ct,"sign_up");
            double caCv = ctCnt2(ct,"add_to_cart"), caCr = ctVal2(ct,"add_to_cart");
            double lCv = ctCnt2(ct,"lead"), lCr = ctVal2(ct,"lead");
            result.put("purchase_cv",(long)pCv); result.put("purchase_cr",(long)pCr);
            result.put("signup_cv",(long)sCv); result.put("signup_cr",(long)sCr);
            result.put("cart_cv",(long)caCv); result.put("cart_cr",(long)caCr);
            result.put("lead_cv",(long)lCv); result.put("lead_cr",(long)lCr);
            result.put("other_cv",0L); result.put("other_cr",0L);
            result.put("purchase_roas", (pCr > 0 && cst > 0) ? fmt(pCr / cst * 100) : 0);
        }
        return result;
    }

    private double ctCnt2(Map<String, Map<String, Object>> ct, String code) {
        return ct.containsKey(code) ? toDouble(ct.get(code), "cnt") : 0.0;
    }

    private double ctVal2(Map<String, Map<String, Object>> ct, String code) {
        return ct.containsKey(code) ? toDouble(ct.get(code), "value") : 0.0;
    }

    private Map<String, Object> sumGraph(List<Map<String, Object>> graph) {
        double im = 0, clk = 0, cst = 0, cv = 0, cr = 0;
        for (Map<String, Object> row : graph) {
            im  += toDouble(row, "im"); clk += toDouble(row, "clk"); cst += toDouble(row, "cst");
            cv  += toDouble(row, "cv"); cr  += toDouble(row, "cr");
        }
        return Map.of("im", im, "clk", clk, "cst", cst, "cv", cv, "cr", cr);
    }

    private Map<String, Object> buildCampaignRow(String cid, String name, String type, int onoff,
                                                   double im, double clk, double cst, double cv, double cr,
                                                   Map<String, Map<String, Object>> convtype,
                                                   boolean hasConvtype) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("campaign_id",            cid);
        row.put("campaign_name",          name != null ? name : "");
        row.put("campaign_type",          type);
        row.put("campaign_status",        onoff);
        row.put("campaign_status_reason", "");
        row.put("im",  Math.round(im)); row.put("clk", Math.round(clk));
        row.put("cst", Math.round(cst)); row.put("cv", Math.round(cv)); row.put("cr", Math.round(cr));
        row.putAll(calcMetrics(im, clk, cst, cv, cr));

        if (hasConvtype && convtype != null) {
            row.put("purchase_cv", ctCnt(convtype, cid, "purchase")); row.put("purchase_cr", ctVal(convtype, cid, "purchase"));
            row.put("signup_cv",   ctCnt(convtype, cid, "sign_up"));  row.put("signup_cr",   ctVal(convtype, cid, "sign_up"));
            row.put("cart_cv",     ctCnt(convtype, cid, "add_to_cart")); row.put("cart_cr", ctVal(convtype, cid, "add_to_cart"));
            row.put("lead_cv",     ctCnt(convtype, cid, "lead"));     row.put("lead_cr",     ctVal(convtype, cid, "lead"));
            row.put("other_cv", 0); row.put("other_cr", 0);
            double purchaseCr = ctVal(convtype, cid, "purchase");
            row.put("purchase_roas", (purchaseCr > 0 && cst > 0) ? fmt(purchaseCr / cst * 100) : 0);
        } else {
            for (String k : List.of("purchase_cv","purchase_cr","signup_cv","signup_cr","cart_cv","cart_cr","lead_cv","lead_cr","other_cv","other_cr","purchase_roas"))
                row.put(k, 0);
        }
        row.put("targets", new ArrayList<>());
        return row;
    }

    private Map<String, Object> getTotalForType(List<Map<String, Object>> list) {
        double im=0,clk=0,cst=0,cv=0,cr=0,pCv=0,pCr=0,sCv=0,sCr=0,cartCv=0,cartCr=0,lCv=0,lCr=0,oCv=0,oCr=0;
        boolean hasConvtype = false;
        for (Map<String, Object> r : list) {
            im+=toDouble(r,"im"); clk+=toDouble(r,"clk"); cst+=toDouble(r,"cst");
            cv+=toDouble(r,"cv"); cr+=toDouble(r,"cr");
            if (r.containsKey("purchase_cv")) {
                hasConvtype = true;
                pCv+=toDouble(r,"purchase_cv"); pCr+=toDouble(r,"purchase_cr");
                sCv+=toDouble(r,"signup_cv"); sCr+=toDouble(r,"signup_cr");
                cartCv+=toDouble(r,"cart_cv"); cartCr+=toDouble(r,"cart_cr");
                lCv+=toDouble(r,"lead_cv"); lCr+=toDouble(r,"lead_cr");
                oCv+=toDouble(r,"other_cv"); oCr+=toDouble(r,"other_cr");
            }
        }
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("im",Math.round(im)); t.put("clk",Math.round(clk));
        t.put("cst",Math.round(cst)); t.put("cv",Math.round(cv)); t.put("cr",Math.round(cr));
        t.putAll(calcMetrics(im,clk,cst,cv,cr));
        if (hasConvtype) {
            t.put("purchase_cv",(long)pCv); t.put("purchase_cr",(long)pCr);
            t.put("signup_cv",(long)sCv); t.put("signup_cr",(long)sCr);
            t.put("cart_cv",(long)cartCv); t.put("cart_cr",(long)cartCr);
            t.put("lead_cv",(long)lCv); t.put("lead_cr",(long)lCr);
            t.put("other_cv",(long)oCv); t.put("other_cr",(long)oCr);
            t.put("purchase_roas",(pCr>0&&cst>0)?fmt(pCr/cst*100):0);
        }
        return t;
    }

    private Map<String, Object> buildCp(Map<String, Object> cur, Map<String, Object> comp) {
        String[] keys = {"im","clk","cst","cv","cr","ctr","cpc","cpa","cvr","roas",
                         "purchase_cv","purchase_cr","signup_cv","signup_cr","cart_cv","cart_cr",
                         "lead_cv","lead_cr","other_cv","other_cr","purchase_roas"};
        Map<String, Object> per = new LinkedHashMap<>();
        Map<String, Object> cp  = new LinkedHashMap<>();
        for (String k : keys) {
            double c = toDouble(cur, k), p = toDouble(comp, k);
            per.put(k, (c > 0 && p > 0) ? fmt((c - p) / p * 100) : 0);
            cp.put(k,  p > 0 ? p : 0);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("per", per);
        result.put("cp",  cp);
        return result;
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

    private Map<String, Document> toMasterMap(List<Document> docs, String idField) {
        Map<String, Document> map = new HashMap<>();
        for (Document d : docs) {
            String id = d.getString(idField);
            if (id != null) map.put(id, d);
        }
        return map;
    }

    private String typeCodeToName(int code, Map<String, Integer> typeMap) {
        for (Map.Entry<String, Integer> e : typeMap.entrySet())
            if (e.getValue() == code) return e.getKey();
        return null;
    }

    /** MongoDB 필드가 String 또는 Integer로 저장되어 있어도 안전하게 int 반환 */
    private int getInt(Document d, String key, int defaultVal) {
        Object v = d.get(key);
        if (v == null) return defaultVal;
        if (v instanceof Integer) return (Integer) v;
        if (v instanceof Long) return ((Long) v).intValue();
        if (v instanceof String) {
            try { return Integer.parseInt((String) v); } catch (NumberFormatException e) { return defaultVal; }
        }
        return defaultVal;
    }

    private double ctCnt(Map<String, Map<String, Object>> ct, String cid, String code) {
        String key = cid + "|" + code;
        return ct.containsKey(key) ? toDouble(ct.get(key), "cnt") : 0.0;
    }

    private double ctVal(Map<String, Map<String, Object>> ct, String cid, String code) {
        String key = cid + "|" + code;
        return ct.containsKey(key) ? toDouble(ct.get(key), "value") : 0.0;
    }

    private double toDouble(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return (v instanceof Number) ? ((Number) v).doubleValue() : 0.0;
    }

    private double fmt(double v) { return Math.round(v * 100.0) / 100.0; }

    private Map<String, Object> emptyReport() {
        return Map.of("day", Map.of("graph", new ArrayList<>()),
                      "cp", new LinkedHashMap<>(),
                      "group", new LinkedHashMap<>(),
                      "total", new LinkedHashMap<>());
    }
}
