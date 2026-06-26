package com.h3.h3_java.api.service.analysis;

import com.h3.h3_java.api.dto.AccountDto;
import com.h3.h3_java.raw.mongo.AccountMongoService;
import com.h3.h3_java.raw.mongo.DashboardMongoService;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class CampaignReportService {

    private final AccountMongoService accountMongo;
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
        AccountDto acc = accountMongo.findAccountDtoByUserId(userId);
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
        List<Map<String, Object>> campComp   = mongoService.aggregateByCampaign(advid, cfrom, cto, "naver_campaign_daily", "daily_advid");
        Map<String, Map<String, Object>> compMap = toCompareMap(campComp);

        List<Document>            masters    = mongoService.findNaverCampaigns(advid);
        Map<String, Document>     masterMap  = new HashMap<>();
        for (Document d : masters) masterMap.put(d.getString("campaignid"), d);

        // 전환유형 캠페인별 (현재/비교)
        Map<String, Map<String, Object>> convtype     = mongoService.aggregateConvtypeByCampaignId(advid, from, to, "naver_campaign_convtype");
        Map<String, Map<String, Object>> convtypeComp = mongoService.aggregateConvtypeByCampaignId(advid, cfrom, cto, "naver_campaign_convtype");

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
            enrichWithCp(row, compMap.get(cid), convtypeComp);
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
        List<Map<String, Object>> campComp  = mongoService.aggregateByCampaign(advid, cfrom, cto, "kakao_sa_campaign_daily", "advkey");
        Map<String, Map<String, Object>> compMap = toCompareMap(campComp);

        List<Document>            masters   = mongoService.findCampaigns(advid, "kakao_sa_campaign");
        Map<String, Document>     masterMap = toMasterMap(masters, "cid");

        List<Map<String, Object>> noneList = new ArrayList<>();
        for (Map<String, Object> cs : campStats) {
            String cid = (String) cs.get("campaign_id");
            Document master = masterMap.get(cid);
            String name = master != null ? master.getString("cname") : cid;
            int onoff   = master != null ? getInt(master, "onoff", 0) : 0;
            Map<String, Object> row = buildCampaignRow(cid, name, "none", onoff,
                toDouble(cs, "im"), toDouble(cs, "clk"), toDouble(cs, "cst"),
                toDouble(cs, "cv"), toDouble(cs, "cr"), null, false);
            enrichWithCp(row, compMap.get(cid), null);
            noneList.add(row);
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
        List<Map<String, Object>> campComp  = mongoService.aggregateByCampaign(advid, cfrom, cto, "kakao_mo_campaign_daily", "advkey");
        Map<String, Map<String, Object>> compMap = toCompareMap(campComp);

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
            Map<String, Object> row = buildCampaignRow(cid, name, typeName, onoff,
                toDouble(cs, "im"), toDouble(cs, "clk"), toDouble(cs, "cst"),
                toDouble(cs, "cv"), toDouble(cs, "cr"), null, false);
            enrichWithCp(row, compMap.get(cid), null);
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

    // ─── 네이버 GFA ───────────────────────────────────────────────────────────

    private Map<String, Object> buildGfaReport(String advid, String from, String to, String cfrom, String cto) {
        if (advid == null || advid.isBlank()) return emptyReport();

        List<Map<String, Object>> graph     = buildGraph(advid, from, to, "naver_gfa_campaign_daily", false);
        Map<String, Object>       compTotal = aggregateTotalFull(advid, cfrom, cto, "naver_gfa_campaign_daily", false, "naver_gfa_campaign_convtype");
        Map<String, Object>       curTotal  = aggregateTotalFull(advid, from, to, "naver_gfa_campaign_daily", false, "naver_gfa_campaign_convtype");

        List<Map<String, Object>> campStats = mongoService.aggregateByCampaign(advid, from, to, "naver_gfa_campaign_daily", "daily_advid");
        List<Map<String, Object>> campComp  = mongoService.aggregateByCampaign(advid, cfrom, cto, "naver_gfa_campaign_daily", "daily_advid");
        Map<String, Map<String, Object>> compMap = toCompareMap(campComp);

        List<Document>            masters   = mongoService.findCampaigns(advid, "naver_gfa_campaign");
        Map<String, Document>     masterMap = toMasterMap(masters, "cid");

        Map<String, Map<String, Object>> convtype     = mongoService.aggregateConvtypeByCampaignId(advid, from, to, "naver_gfa_campaign_convtype");
        Map<String, Map<String, Object>> convtypeComp = mongoService.aggregateConvtypeByCampaignId(advid, cfrom, cto, "naver_gfa_campaign_convtype");

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
            Map<String, Object> row = buildCampaignRow(cid, master.getString("cname"), typeName,
                getInt(master, "onoff", 0), toDouble(cs, "im"), toDouble(cs, "clk"),
                cst, toDouble(cs, "cv"), toDouble(cs, "cr"), convtype, true);
            enrichWithCp(row, compMap.get(cid), convtypeComp);
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

    // ─── 구글 ─────────────────────────────────────────────────────────────────

    private Map<String, Object> buildGoogleReport(String advid, String from, String to, String cfrom, String cto) {
        if (advid == null || advid.isBlank()) return emptyReport();

        List<Map<String, Object>> graph     = buildGraph(advid, from, to, "google_campaign_daily", false);
        Map<String, Object>       compTotal = aggregateTotal(advid, cfrom, cto, "google_campaign_daily", false);
        Map<String, Object>       curTotal  = sumGraph(graph);

        List<Map<String, Object>> campStats = mongoService.aggregateByCampaign(advid, from, to, "google_campaign_daily", "daily_advid");
        List<Map<String, Object>> campComp  = mongoService.aggregateByCampaign(advid, cfrom, cto, "google_campaign_daily", "daily_advid");
        Map<String, Map<String, Object>> compMap = toCompareMap(campComp);

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
            if (groups.containsKey(typeName)) {
                Map<String, Object> row = buildCampaignRow(cid, name, typeName, onoff,
                    toDouble(cs, "im"), toDouble(cs, "clk"), cst, cv, cr, null, false);
                enrichWithCp(row, compMap.get(cid), null);
                groups.get(typeName).add(row);
            }
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
        double im  = toDouble(r, "im"),  clk = toDouble(r, "clk");
        double cst = vatNaver ? toDouble(r, "cst") * 1.1 : toDouble(r, "cst");
        double cv  = toDouble(r, "cv"),  cr  = toDouble(r, "cr");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("im", im); result.put("clk", clk);
        result.put("cst", cst); result.put("cv", cv); result.put("cr", cr);
        result.putAll(calcMetrics(im, clk, cst, cv, cr));
        return result;
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
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("im", im); result.put("clk", clk);
        result.put("cst", cst); result.put("cv", cv); result.put("cr", cr);
        result.putAll(calcMetrics(im, clk, cst, cv, cr));
        return result;
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

    /** 캠페인별 비교기간 stats → campaign_id 키 Map */
    private Map<String, Map<String, Object>> toCompareMap(List<Map<String, Object>> compStats) {
        Map<String, Map<String, Object>> map = new HashMap<>();
        for (Map<String, Object> cs : compStats) {
            String cid = (String) cs.get("campaign_id");
            if (cid != null) map.put(cid, cs);
        }
        return map;
    }

    /** 캠페인 row에 cp/diff/per 서브맵 추가 (전 지표 21개) */
    private void enrichWithCp(Map<String, Object> row,
                               Map<String, Object> compStat,
                               Map<String, Map<String, Object>> compConvtype) {
        String cid = (String) row.get("campaign_id");

        // 현재 기간
        double curIm   = toDouble(row, "im");
        double curClk  = toDouble(row, "clk");
        double curCst  = toDouble(row, "cst");
        double curCv   = toDouble(row, "cv");
        double curCr   = toDouble(row, "cr");
        double curCtr  = toDouble(row, "ctr");
        double curCpc  = toDouble(row, "cpc");
        double curCpa  = toDouble(row, "cpa");
        double curCvr  = toDouble(row, "cvr");
        double curRoas = toDouble(row, "roas");
        double curPRoas = toDouble(row, "purchase_roas");
        double curPCv  = toDouble(row, "purchase_cv");
        double curPCr  = toDouble(row, "purchase_cr");
        double curSCv  = toDouble(row, "signup_cv");
        double curSCr  = toDouble(row, "signup_cr");
        double curCaCv = toDouble(row, "cart_cv");
        double curCaCr = toDouble(row, "cart_cr");
        double curLCv  = toDouble(row, "lead_cv");
        double curLCr  = toDouble(row, "lead_cr");
        double curOCv  = toDouble(row, "other_cv");
        double curOCr  = toDouble(row, "other_cr");

        // 비교 기간 raw
        double compIm  = compStat != null ? toDouble(compStat, "im")  : 0;
        double compClk = compStat != null ? toDouble(compStat, "clk") : 0;
        double compCst = compStat != null ? toDouble(compStat, "cst") : 0;
        double compCv  = compStat != null ? toDouble(compStat, "cv")  : 0;
        double compCr  = compStat != null ? toDouble(compStat, "cr")  : 0;

        // 비교 기간 파생 지표
        double compCtr  = (compClk > 0 && compIm  > 0) ? fmt(compClk / compIm  * 100) : 0;
        double compCpc  = (compCst > 0 && compClk > 0) ? fmt(compCst / compClk)        : 0;
        double compCpa  = (compCst > 0 && compCv  > 0) ? fmt(compCst / compCv)         : 0;
        double compCvr  = (compCv  > 0 && compClk > 0) ? fmt(compCv  / compClk * 100)  : 0;
        double compRoas = (compCr  > 0 && compCst > 0) ? fmt(compCr  / compCst * 100)  : 0;

        // 비교 기간 전환유형
        double compPCv  = (compConvtype != null && cid != null) ? ctCnt(compConvtype, cid, "purchase")    : 0;
        double compPCr  = (compConvtype != null && cid != null) ? ctVal(compConvtype, cid, "purchase")    : 0;
        double compSCv  = (compConvtype != null && cid != null) ? ctCnt(compConvtype, cid, "sign_up")     : 0;
        double compSCr  = (compConvtype != null && cid != null) ? ctVal(compConvtype, cid, "sign_up")     : 0;
        double compCaCv = (compConvtype != null && cid != null) ? ctCnt(compConvtype, cid, "add_to_cart") : 0;
        double compCaCr = (compConvtype != null && cid != null) ? ctVal(compConvtype, cid, "add_to_cart") : 0;
        double compLCv  = (compConvtype != null && cid != null) ? ctCnt(compConvtype, cid, "lead")        : 0;
        double compLCr  = (compConvtype != null && cid != null) ? ctVal(compConvtype, cid, "lead")        : 0;
        double compPRoas = (compPCr > 0 && compCst > 0) ? fmt(compPCr / compCst * 100) : 0;

        Map<String, Object> cp   = new LinkedHashMap<>();
        Map<String, Object> diff = new LinkedHashMap<>();
        Map<String, Object> per  = new LinkedHashMap<>();

        // cp (비교 기간 값)
        cp.put("im",   Math.round(compIm));  cp.put("clk",  Math.round(compClk));
        cp.put("cst",  Math.round(compCst)); cp.put("cv",   Math.round(compCv));  cp.put("cr",  Math.round(compCr));
        cp.put("ctr",  compCtr);             cp.put("cpc",  compCpc);             cp.put("cpa", compCpa);
        cp.put("cvr",  compCvr);             cp.put("roas", compRoas);
        cp.put("purchase_cv",  (long) compPCv);  cp.put("purchase_cr",  (long) compPCr);  cp.put("purchase_roas", compPRoas);
        cp.put("signup_cv",    (long) compSCv);  cp.put("signup_cr",    (long) compSCr);
        cp.put("cart_cv",      (long) compCaCv); cp.put("cart_cr",      (long) compCaCr);
        cp.put("lead_cv",      (long) compLCv);  cp.put("lead_cr",      (long) compLCr);
        cp.put("other_cv",     0L);              cp.put("other_cr",     0L);

        // diff (절대 증감)
        diff.put("im",   Math.round(curIm  - compIm));  diff.put("clk",  Math.round(curClk  - compClk));
        diff.put("cst",  Math.round(curCst - compCst)); diff.put("cv",   Math.round(curCv   - compCv));  diff.put("cr",  Math.round(curCr  - compCr));
        diff.put("ctr",  fmt(curCtr  - compCtr));       diff.put("cpc",  fmt(curCpc  - compCpc));        diff.put("cpa", fmt(curCpa  - compCpa));
        diff.put("cvr",  fmt(curCvr  - compCvr));       diff.put("roas", fmt(curRoas - compRoas));
        diff.put("purchase_cv",  Math.round(curPCv  - compPCv));  diff.put("purchase_cr",  Math.round(curPCr  - compPCr));  diff.put("purchase_roas", fmt(curPRoas - compPRoas));
        diff.put("signup_cv",    Math.round(curSCv  - compSCv));  diff.put("signup_cr",    Math.round(curSCr  - compSCr));
        diff.put("cart_cv",      Math.round(curCaCv - compCaCv)); diff.put("cart_cr",      Math.round(curCaCr - compCaCr));
        diff.put("lead_cv",      Math.round(curLCv  - compLCv));  diff.put("lead_cr",      Math.round(curLCr  - compLCr));
        diff.put("other_cv",     Math.round(curOCv));              diff.put("other_cr",     Math.round(curOCr));

        // per (% 증감률)
        per.put("im",   compIm   > 0 ? fmt((curIm   - compIm)   / compIm   * 100) : 0);
        per.put("clk",  compClk  > 0 ? fmt((curClk  - compClk)  / compClk  * 100) : 0);
        per.put("cst",  compCst  > 0 ? fmt((curCst  - compCst)  / compCst  * 100) : 0);
        per.put("cv",   compCv   > 0 ? fmt((curCv   - compCv)   / compCv   * 100) : 0);
        per.put("cr",   compCr   > 0 ? fmt((curCr   - compCr)   / compCr   * 100) : 0);
        per.put("ctr",  compCtr  > 0 ? fmt((curCtr  - compCtr)  / compCtr  * 100) : 0);
        per.put("cpc",  compCpc  > 0 ? fmt((curCpc  - compCpc)  / compCpc  * 100) : 0);
        per.put("cpa",  compCpa  > 0 ? fmt((curCpa  - compCpa)  / compCpa  * 100) : 0);
        per.put("cvr",  compCvr  > 0 ? fmt((curCvr  - compCvr)  / compCvr  * 100) : 0);
        per.put("roas", compRoas > 0 ? fmt((curRoas - compRoas) / compRoas * 100) : 0);
        per.put("purchase_cv",   compPCv   > 0 ? fmt((curPCv   - compPCv)   / compPCv   * 100) : 0);
        per.put("purchase_cr",   compPCr   > 0 ? fmt((curPCr   - compPCr)   / compPCr   * 100) : 0);
        per.put("purchase_roas", compPRoas > 0 ? fmt((curPRoas - compPRoas) / compPRoas * 100) : 0);
        per.put("signup_cv",     compSCv   > 0 ? fmt((curSCv   - compSCv)   / compSCv   * 100) : 0);
        per.put("signup_cr",     compSCr   > 0 ? fmt((curSCr   - compSCr)   / compSCr   * 100) : 0);
        per.put("cart_cv",       compCaCv  > 0 ? fmt((curCaCv  - compCaCv)  / compCaCv  * 100) : 0);
        per.put("cart_cr",       compCaCr  > 0 ? fmt((curCaCr  - compCaCr)  / compCaCr  * 100) : 0);
        per.put("lead_cv",       compLCv   > 0 ? fmt((curLCv   - compLCv)   / compLCv   * 100) : 0);
        per.put("lead_cr",       compLCr   > 0 ? fmt((curLCr   - compLCr)   / compLCr   * 100) : 0);
        per.put("other_cv",      0);
        per.put("other_cr",      0);

        row.put("cp",   cp);
        row.put("diff", diff);
        row.put("per",  per);
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
