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
public class AdgroupKeywordReportService {

    private final AccountMongoService accountMongo;
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

    public Map<String, Object> getAdgroupKeywordReport(String userId, String md, String fromdate, String todate,
                                                        String cfrom, String cto, String kpi, String adgroupId) {
        AccountDto acc = accountMongo.findAccountDtoByUserId(userId);
        if (acc == null) return fail("1009", "계정을 확인해 주세요.");

        String comparefrom = (cfrom != null && !cfrom.isBlank()) ? cfrom : fromdate;
        String compareto   = (cto   != null && !cto.isBlank())   ? cto   : todate;

        return getKeywordTop(acc, md, fromdate, todate, comparefrom, compareto, kpi, adgroupId);
    }

    private Map<String, Object> getKeywordTop(AccountDto acc, String md, String from, String to,
                                               String cfrom, String cto, String kpi, String adgroupId) {
        String[] kpis = (kpi != null && !kpi.isBlank()) ? kpi.split(",") : new String[]{"cst"};
        Map<String, List<Map<String, Object>>> allStats = new LinkedHashMap<>();
        for (String k : kpis) allStats.put(k.trim(), new ArrayList<>());

        Map<String, Map<String, Object>> compStatsByKwid = new HashMap<>();

        for (String targetMd : getMdList(md)) {
            KwConfig cfg = MD_MAP.get(targetMd);
            if (cfg == null) continue;
            String advid = getAdvid(acc, targetMd);
            if (advid == null || advid.isBlank()) continue;

            List<Map<String, Object>> stats = mongoService.aggregateByKeywordWithFilter(
                advid, from, to, cfg.dailyCol(), cfg.advField(), "adgroup_id", adgroupId);
            if (stats.isEmpty()) continue;

            List<Map<String, Object>> compStats = mongoService.aggregateByKeywordWithFilter(
                advid, cfrom, cto, cfg.dailyCol(), cfg.advField(), "adgroup_id", adgroupId);
            for (Map<String, Object> s : compStats) {
                String kwid = (String) s.get("keyword_id");
                if (kwid != null) compStatsByKwid.put(kwid, s);
            }

            Map<String, Map<String, Object>> kwMasterMap = buildKwMasterMap(advid, cfg, stats);
            Map<String, String> agNameMap   = buildNameMap(advid, cfg.agMasterCol(), "gid", "gname");
            Map<String, String> campNameMap = buildNameMap(advid, cfg.campMasterCol(), cfg.campIdField(), cfg.campNameField());
            Map<String, Map<String, Object>> kwConvtype = cfg.convtypeCol() != null
                ? mongoService.aggregateConvtypeByKeywordId(advid, from, to, cfg.convtypeCol())
                : new HashMap<>();

            for (Map<String, Object> stat : stats) {
                Map<String, Object> row = buildRow(stat, kwMasterMap, agNameMap, campNameMap, kwConvtype);
                for (String k : kpis) allStats.get(k.trim()).add(new LinkedHashMap<>(row));
            }
        }

        Map<String, Object> kpisResult = new LinkedHashMap<>();
        for (String k : kpis) {
            String t = k.trim();
            List<Map<String, Object>> rows = allStats.get(t);
            rows.sort((a, b) -> Double.compare(toDoubleObj(b.get(t)), toDoubleObj(a.get(t))));
            List<Map<String, Object>> top10 = rows.size() > 10 ? new ArrayList<>(rows.subList(0, 10)) : rows;

            for (Map<String, Object> row : top10) {
                String kwid = (String) row.get("keyword_id");
                Map<String, Object> compStat = compStatsByKwid.get(kwid);
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
            kpisResult.put(t, top10);
        }

        if (kpisResult.values().stream().allMatch(v -> ((List<?>) v).isEmpty())) return noData();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("keywords", "");
        data.put("kpis",     kpisResult);
        data.put("total",    "");

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("result", "success"); res.put("status", "200");
        res.put("data", data);
        res.put("resultcount", 0); res.put("totalcount", 0);
        return res;
    }

    private Map<String, Object> buildRow(Map<String, Object> stat,
                                          Map<String, Map<String, Object>> kwMasterMap,
                                          Map<String, String> agNameMap,
                                          Map<String, String> campNameMap,
                                          Map<String, Map<String, Object>> kwConvtype) {
        String kwid = (String) stat.get("keyword_id");
        String agid = (String) stat.get("adgroup_id");
        String cid  = (String) stat.get("campaign_id");

        Map<String, Object> kwMaster = kwMasterMap.getOrDefault(kwid != null ? kwid : "", Collections.emptyMap());
        double im=toDouble(stat,"im"), clk=toDouble(stat,"clk"), cst=toDouble(stat,"cst"),
               cv=toDouble(stat,"cv"), cr=toDouble(stat,"cr");

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("campaign_id",       cid  != null ? cid  : "");
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

    private List<String> getMdList(String md) {
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
