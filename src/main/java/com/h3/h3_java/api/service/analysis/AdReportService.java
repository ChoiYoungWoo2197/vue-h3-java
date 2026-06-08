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
        "N", new AdConfig("naver_ad_daily",    "naver_ad",    "naver_adgroup",    "naver_campaign",
                          "daily_advid", "campaignid", "campaignname", "adid",  "naver_ad_convtype",  true),
        "D", new AdConfig("kakao_sa_ad_daily", "kakao_sa_ad", "kakao_sa_adgroup", "kakao_sa_campaign",
                          "advkey",      "cid",        "cname",        "aid",   null,                  false),
        "K", new AdConfig("kakao_mo_ad_daily", "kakao_mo_ad", "kakao_mo_adgroup", "kakao_mo_campaign",
                          "advkey",      "cid",        "cname",        "aid",   null,                  false)
    );

    public Map<String, Object> getAdReport(String userId, String md, String fromdate, String todate,
                                             String kpi, String sort, int start, int display) {
        AccountDto acc = accountMapper.selectByUserId(userId);
        if (acc == null) return fail("1009", "계정을 확인해 주세요.");

        if (kpi != null && !kpi.isBlank()) {
            return getAdTop(acc, md, fromdate, todate, kpi);
        } else {
            return getAd(acc, md, fromdate, todate, sort, start, display);
        }
    }

    // ─── 소재 목록 ────────────────────────────────────────────────────────────

    private Map<String, Object> getAd(AccountDto acc, String md, String from, String to,
                                       String sort, int start, int display) {
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

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("result", "success"); res.put("status", "200");
        res.put("data",  Map.of("ads", paged, "topads", ""));
        res.put("resultcount", 0); res.put("totalcount", total);
        return res;
    }

    // ─── KPI별 top 10 ────────────────────────────────────────────────────────

    private Map<String, Object> getAdTop(AccountDto acc, String md, String from, String to, String kpi) {
        String[] kpis = kpi.split(",");
        List<Map<String, Object>> allRows = new ArrayList<>();

        // 비교기간 ad_id → stats
        Map<String, Map<String, Object>> compStatsByAdId = new HashMap<>();

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

        if (allRows.isEmpty()) {
            Map<String, Object> topads = new LinkedHashMap<>();
            for (String k : kpis) topads.put(k.trim(), Collections.emptyList());
            Map<String, Object> res = new LinkedHashMap<>();
            res.put("result","success"); res.put("status","200");
            res.put("data", Map.of("ads","","topads",topads));
            res.put("resultcount",0); res.put("totalcount",0);
            return res;
        }

        Map<String, Object> topads = new LinkedHashMap<>();
        for (String k : kpis) {
            String t = k.trim();
            List<Map<String, Object>> sorted = new ArrayList<>(allRows);
            sorted.sort((a, b) -> Double.compare(toDoubleObj(b.get(t)), toDoubleObj(a.get(t))));
            List<Map<String, Object>> top10 = sorted.size() > 10 ? new ArrayList<>(sorted.subList(0, 10)) : sorted;
            topads.put(t, top10);
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

        // convtype
        double pCv=ctCnt(adConvtype,adid,"purchase"), pCr=ctVal(adConvtype,adid,"purchase");
        row.put("purchase_cv",(long)pCv); row.put("purchase_cr",(long)pCr);
        row.put("signup_cv",(long)ctCnt(adConvtype,adid,"sign_up")); row.put("signup_cr",(long)ctVal(adConvtype,adid,"sign_up"));
        row.put("cart_cv",(long)ctCnt(adConvtype,adid,"add_to_cart")); row.put("cart_cr",(long)ctVal(adConvtype,adid,"add_to_cart"));
        row.put("lead_cv",(long)ctCnt(adConvtype,adid,"lead")); row.put("lead_cr",(long)ctVal(adConvtype,adid,"lead"));
        row.put("other_cv",0L); row.put("other_cr",0L);
        row.put("purchase_roas", (pCr>0&&cst>0) ? fmt(pCr/cst*100) : 0);

        // ad master 추가 필드
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
            case "headline" -> cfg.isNaver() ? str(ad,"subject") : str(ad,"headline");
            case "pc_url"   -> cfg.isNaver() ? str(ad,"plandingurl") : str(ad,"purl");
            default -> "";
        };
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
        if ("TOTAL".equals(md)) return List.of("N", "D");
        if (MD_MAP.containsKey(md)) return List.of(md);
        return List.of("N");
    }

    private String getAdvid(AccountDto acc, String md) {
        return switch (md) {
            case "D" -> acc.getAccountKakaosa();
            case "K" -> acc.getAccountKakaomoment();
            default  -> acc.getAccountNaverCustomer();
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
