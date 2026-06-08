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
                    String adMasterIdField, boolean naverOnoff) {}

    private static final Map<String, AdConfig> MD_MAP = Map.of(
        "N", new AdConfig("naver_ad_daily",    "naver_ad",    "naver_adgroup",    "naver_campaign",
                          "daily_advid", "campaignid", "campaignname", "adid",  true),
        "D", new AdConfig("kakao_sa_ad_daily", "kakao_sa_ad", "kakao_sa_adgroup", "kakao_sa_campaign",
                          "advkey",      "cid",        "cname",        "aid",   false),
        "K", new AdConfig("kakao_mo_ad_daily", "kakao_mo_ad", "kakao_mo_adgroup", "kakao_mo_campaign",
                          "advkey",      "cid",        "cname",        "aid",   false)
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

            for (Map<String, Object> stat : stats) {
                allRows.add(buildRow(stat, adMasterMap, agMasterMap, campMasterMap, cfg, false));
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

    // ─── KPI별 max/min ───────────────────────────────────────────────────────

    private Map<String, Object> getAdTop(AccountDto acc, String md, String from, String to, String kpi) {
        String[] kpis = kpi.split(",");
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

            for (Map<String, Object> stat : stats) {
                allRows.add(buildRow(stat, adMasterMap, agMasterMap, campMasterMap, cfg, true));
            }
        }

        Map<String, Object> topads = new LinkedHashMap<>();
        for (String k : kpis) {
            String t = k.trim();
            List<Map<String, Object>> sorted = new ArrayList<>(allRows);
            sorted.sort((a, b) -> {
                double da = toDoubleObj(a.get(t)), db = toDoubleObj(b.get(t));
                return Double.compare(db, da);
            });
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("max", sorted.isEmpty() ? Collections.emptyList() : List.of(sorted.get(0)));
            entry.put("min", sorted.isEmpty() ? Collections.emptyList() : List.of(sorted.get(sorted.size() - 1)));
            topads.put(t, entry);
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("result", "success"); res.put("status", "200");
        res.put("data",  Map.of("ads", "", "topads", topads));
        res.put("resultcount", 0); res.put("totalcount", 0);
        return res;
    }

    // ─── 행 빌드 ─────────────────────────────────────────────────────────────

    private Map<String, Object> buildRow(Map<String, Object> stat,
                                          Map<String, Document> adMasterMap,
                                          Map<String, Document> agMasterMap,
                                          Map<String, Document> campMasterMap,
                                          AdConfig cfg, boolean includeExtra) {
        String adid = (String) stat.get("ad_id");
        String agid = (String) stat.get("adgroup_id");
        String cid  = (String) stat.get("campaign_id");

        Document adM   = adMasterMap.getOrDefault(adid != null ? adid : "", new Document());
        Document agM   = agMasterMap.getOrDefault(agid != null ? agid : "", new Document());
        Document campM = campMasterMap.getOrDefault(cid  != null ? cid  : "", new Document());

        double im=toDouble(stat,"im"), clk=toDouble(stat,"clk"), cst=toDouble(stat,"cst"),
               cv=toDouble(stat,"cv"), cr=toDouble(stat,"cr");

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("campaignid",            cid  != null ? cid  : "");
        row.put("campaign_name",         campM.getString(cfg.campNameField()) != null ? campM.getString(cfg.campNameField()) : "");
        row.put("campaigntype",          "");
        row.put("campaign_status",       "");
        row.put("campaign_status_reason","");
        row.put("adgroup_id",            agid != null ? agid : "");
        row.put("adgroup_name",          agM.getString("gname") != null ? agM.getString("gname") : "");
        row.put("adgroup_status",        "");
        row.put("adgroup_status_reason", "");
        row.put("ad_id",                 adid != null ? adid : "");
        row.put("ad_description",        getAdField(adM, cfg, "description"));
        row.put("ad_headline",           getAdField(adM, cfg, "headline"));
        row.put("ad_mo_display",         getAdField(adM, cfg, "mo_url"));
        row.put("ad_mo_final",           getAdField(adM, cfg, "mo_url"));
        row.put("ad_pc_display",         getAdField(adM, cfg, "pc_url"));
        row.put("ad_pc_final",           getAdField(adM, cfg, "pc_url"));
        row.put("ad_status",             "");
        row.put("ad_status_reason",      "");
        row.put("ad_type",               "");
        row.put("im",  Math.round(im));  row.put("clk", Math.round(clk));
        row.put("cst", Math.round(cst)); row.put("cv",  Math.round(cv)); row.put("cr", Math.round(cr));
        row.putAll(calcMetrics(im, clk, cst, cv, cr));
        return row;
    }

    private String getAdField(Document ad, AdConfig cfg, String field) {
        boolean isNaver = cfg.naverOnoff();
        return switch (field) {
            case "description" -> ad.getString("description") != null ? ad.getString("description") : "";
            case "headline"    -> isNaver ? (ad.getString("subject") != null ? ad.getString("subject") : "")
                                          : (ad.getString("headline") != null ? ad.getString("headline") : "");
            case "mo_url"      -> isNaver ? (ad.getString("mlandingurl") != null ? ad.getString("mlandingurl") : "")
                                          : (ad.getString("murl") != null ? ad.getString("murl") : "");
            case "pc_url"      -> isNaver ? (ad.getString("plandingurl") != null ? ad.getString("plandingurl") : "")
                                          : (ad.getString("purl") != null ? ad.getString("purl") : "");
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

    private double fmt(double v)         { return Math.round(v * 100.0) / 100.0; }
    private double toDouble(Map<String, Object> m, String k) { Object v=m.get(k); return v instanceof Number n ? n.doubleValue() : 0.0; }
    private double toDoubleObj(Object v) { return v instanceof Number n ? n.doubleValue() : 0.0; }
    private Map<String, Object> fail(String status, String msg) { return Map.of("result","failed","status",status,"errormessage",msg); }
    private Map<String, Object> noData() { return Map.of("result","success","status","1004","errormessage","검색 결과가 없습니다."); }
}
