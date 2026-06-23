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
public class CampaignAdReportService {

    private final AccountMongoService accountMongo;
    private final DashboardMongoService mongoService;

    record AdConfig(String dailyCol, String adMasterCol, String agMasterCol, String campMasterCol,
                    String advField, String campIdField, String campNameField,
                    String adMasterIdField, String convtypeCol, boolean isNaver) {}

    private static final Map<String, AdConfig> MD_MAP = Map.of(
        "N", new AdConfig("naver_ad_daily",    "naver_ad",    "naver_adgroup",    "naver_campaign",
                          "daily_advid", "campaignid", "campaignname", "adid", "naver_ad_convtype", true),
        "D", new AdConfig("kakao_sa_ad_daily", "kakao_sa_ad", "kakao_sa_adgroup", "kakao_sa_campaign",
                          "advkey",      "cid",        "cname",        "aid",  null,                false)
    );

    public Map<String, Object> getCampaignAdReport(String userId, String md, String fromdate, String todate,
                                                    String cfrom, String cto, String kpi, String campaignId) {
        AccountDto acc = accountMongo.findAccountDtoByUserId(userId);
        if (acc == null) return fail("1009", "계정을 확인해 주세요.");
        String comparefrom = (cfrom != null && !cfrom.isBlank()) ? cfrom : fromdate;
        String compareto   = (cto   != null && !cto.isBlank())   ? cto   : todate;
        return getAdTop(acc, md, fromdate, todate, comparefrom, compareto, kpi, "campaign_id", campaignId);
    }

    private Map<String, Object> getAdTop(AccountDto acc, String md, String from, String to,
                                          String cfrom, String cto, String kpi,
                                          String filterField, String filterValue) {
        String[] kpis = (kpi != null && !kpi.isBlank()) ? kpi.split(",") : new String[]{"cst"};

        Map<String, List<Map<String, Object>>> allStats = new LinkedHashMap<>();
        for (String k : kpis) allStats.put(k.trim(), new ArrayList<>());
        Map<String, Map<String, Object>> compStatsByAdId = new HashMap<>();

        for (String targetMd : getMdList(md)) {
            AdConfig cfg = MD_MAP.get(targetMd);
            if (cfg == null) continue;
            String advid = getAdvid(acc, targetMd);
            if (advid == null || advid.isBlank()) continue;

            List<Map<String, Object>> stats = mongoService.aggregateByAdWithFilter(
                advid, from, to, cfg.dailyCol(), cfg.advField(), filterField, filterValue);
            if (stats.isEmpty()) continue;

            List<Map<String, Object>> compStats = mongoService.aggregateByAdWithFilter(
                advid, cfrom, cto, cfg.dailyCol(), cfg.advField(), filterField, filterValue);
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
                for (String k : kpis) allStats.get(k.trim()).add(new LinkedHashMap<>(row));
            }
        }

        Map<String, Object> topads = new LinkedHashMap<>();
        for (String k : kpis) {
            String t = k.trim();
            List<Map<String, Object>> rows = allStats.get(t);
            if (rows.isEmpty()) { topads.put(t, Collections.emptyList()); continue; }

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

        if (topads.values().stream().allMatch(v -> ((List<?>) v).isEmpty())) return noData();

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("result", "success"); res.put("status", "200");
        res.put("data", Map.of("ads", "", "topads", topads, "total", ""));
        res.put("resultcount", 0); res.put("totalcount", 0);
        return res;
    }

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
        row.put("campaign_id",    cid  != null ? cid  : "");
        row.put("campaign_name",  str(campM, cfg.campNameField()));
        row.put("adgroup_id",     agid != null ? agid : "");
        row.put("adgroup_name",   str(agM, "gname"));
        row.put("ad_id",          adid != null ? adid : "");
        row.put("ad_headline",    cfg.isNaver() ? str(adM, "subject")     : str(adM, "headline"));
        row.put("ad_description", str(adM, "description"));
        row.put("ad_pc_display",  cfg.isNaver() ? str(adM, "plandingurl") : str(adM, "purl"));
        row.put("ad_pc_final",    cfg.isNaver() ? str(adM, "plandingurl") : str(adM, "purl"));
        row.put("im",  Math.round(im));  row.put("clk", Math.round(clk));
        row.put("cst", Math.round(cst)); row.put("cv",  Math.round(cv)); row.put("cr", Math.round(cr));
        row.putAll(calcMetrics(im, clk, cst, cv, cr));

        double pCv = ctCnt(adConvtype, adid, "purchase");
        double pCr = ctVal(adConvtype, adid, "purchase");
        row.put("purchase_cv", (long) pCv); row.put("purchase_cr", (long) pCr);
        row.put("signup_cv",   (long) ctCnt(adConvtype, adid, "sign_up"));
        row.put("signup_cr",   (long) ctVal(adConvtype, adid, "sign_up"));
        row.put("cart_cv",     (long) ctCnt(adConvtype, adid, "add_to_cart"));
        row.put("cart_cr",     (long) ctVal(adConvtype, adid, "add_to_cart"));
        row.put("lead_cv",     (long) ctCnt(adConvtype, adid, "lead"));
        row.put("lead_cr",     (long) ctVal(adConvtype, adid, "lead"));
        row.put("other_cv",    0L); row.put("other_cr", 0L);
        row.put("purchase_roas", (pCr > 0 && cst > 0) ? fmt(pCr / cst * 100) : 0);
        row.put("ad_imgurl1",  str(adM, "imgurl1"));
        row.put("ad_imgurl2",  str(adM, "imgurl2"));
        row.put("ad_imgurl3",  str(adM, "imgurl3"));
        Object typeVal = adM.get("type");
        row.put("ad_type", typeVal instanceof Number n ? n.intValue() : "");
        row.put("ad_image_pbase64", "");
        return row;
    }

    private Map<String, Document> buildMasterMap(String advid, String collection, String idField) {
        Map<String, Document> map = new HashMap<>();
        for (Document d : mongoService.findCampaigns(advid, collection)) {
            String id = d.getString(idField);
            if (id != null) map.put(id, d);
        }
        return map;
    }

    private List<String> getMdList(String md) {
        if (MD_MAP.containsKey(md)) return List.of(md);
        return List.of("N");
    }

    private String getAdvid(AccountDto acc, String md) {
        return "D".equals(md) ? acc.getAccountKakaosa() : acc.getAccountNaverCustomer();
    }

    private double ctCnt(Map<String, Map<String, Object>> ct, String adid, String code) {
        if (adid == null) return 0.0;
        Map<String, Object> v = ct.get(adid + "|" + code);
        return v != null ? toDoubleObj(v.get("cnt")) : 0.0;
    }

    private double ctVal(Map<String, Map<String, Object>> ct, String adid, String code) {
        if (adid == null) return 0.0;
        Map<String, Object> v = ct.get(adid + "|" + code);
        return v != null ? toDoubleObj(v.get("value")) : 0.0;
    }

    private String str(Document d, String key) { return d != null && d.getString(key) != null ? d.getString(key) : ""; }
    private Map<String, Object> calcMetrics(double im, double clk, double cst, double cv, double cr) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ctr",  (im>0&&clk>0)  ? fmt(clk/im*100)  : 0);
        m.put("cpc",  (cst>0&&clk>0) ? fmt(cst/clk)     : 0);
        m.put("cpa",  (cst>0&&cv>0)  ? fmt(cst/cv)      : 0);
        m.put("cvr",  (cv>0&&clk>0)  ? fmt(cv/clk*100)  : 0);
        m.put("roas", (cr>0&&cst>0)  ? fmt(cr/cst*100)  : 0);
        return m;
    }
    private double fmt(double v)         { return Math.round(v * 100.0) / 100.0; }
    private double toDouble(Map<String, Object> m, String k) { Object v=m.get(k); return v instanceof Number n ? n.doubleValue() : 0.0; }
    private double toDoubleObj(Object v) { return v instanceof Number n ? n.doubleValue() : 0.0; }
    private Map<String, Object> fail(String s, String msg) { return Map.of("result","failed","status",s,"errormessage",msg); }
    private Map<String, Object> noData() { return Map.of("result","success","status","1004","errormessage","검색 결과가 없습니다."); }
}
