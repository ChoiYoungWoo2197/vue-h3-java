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
                    String advField, String campIdField, String campNameField, boolean kwordInDaily) {}

    private static final Map<String, KwConfig> MD_MAP = Map.of(
        "N", new KwConfig("naver_keyword_daily",    "naver_keyword",    "naver_adgroup",    "naver_campaign",
                          "daily_advid", "campaignid", "campaignname", false),
        "D", new KwConfig("kakao_sa_keyword_daily", "kakao_sa_keyword", "kakao_sa_adgroup", "kakao_sa_campaign",
                          "advkey",      "cid",        "cname",         true)
    );

    public Map<String, Object> getKeywordReport(String userId, String md, String fromdate, String todate,
                                                  String kpi, String sort, int start, int display) {
        AccountDto acc = accountMapper.selectByUserId(userId);
        if (acc == null) return fail("1009", "계정을 확인해 주세요.");

        if (kpi != null && !kpi.isBlank()) {
            return getKeywordTop(acc, md, fromdate, todate, kpi);
        } else {
            return getKeyword(acc, md, fromdate, todate, sort, start, display);
        }
    }

    // ─── 일반 목록 ────────────────────────────────────────────────────────────

    private Map<String, Object> getKeyword(AccountDto acc, String md, String from, String to,
                                            String sort, int start, int display) {
        List<Map<String, Object>> allRows = new ArrayList<>();

        for (String targetMd : getMdList(md)) {
            KwConfig cfg = MD_MAP.get(targetMd);
            if (cfg == null) continue;
            String advid = getAdvid(acc, targetMd);
            if (advid == null || advid.isBlank()) continue;

            List<Map<String, Object>> stats = mongoService.aggregateByKeyword(advid, from, to, cfg.dailyCol(), cfg.advField());
            if (stats.isEmpty()) continue;

            Map<String, String> kwNameMap = buildKwNameMap(advid, cfg, stats);
            Map<String, String> agNameMap = buildNameMap(advid, cfg.agMasterCol(), "gid", "gname");
            Map<String, String> campNameMap = buildNameMap(advid, cfg.campMasterCol(), cfg.campIdField(), cfg.campNameField());

            for (Map<String, Object> stat : stats) {
                allRows.add(buildRow(stat, kwNameMap, agNameMap, campNameMap, false));
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
        res.put("data", Map.of("keywords", paged));
        res.put("resultcount", 0);
        res.put("totalcount", total);
        return res;
    }

    // ─── KPI 상위 10개 ────────────────────────────────────────────────────────

    private Map<String, Object> getKeywordTop(AccountDto acc, String md, String from, String to, String kpi) {
        String[] kpis = kpi.split(",");
        Map<String, List<Map<String, Object>>> allStats = new LinkedHashMap<>();

        for (String targetMd : getMdList(md)) {
            KwConfig cfg = MD_MAP.get(targetMd);
            if (cfg == null) continue;
            String advid = getAdvid(acc, targetMd);
            if (advid == null || advid.isBlank()) continue;

            List<Map<String, Object>> stats = mongoService.aggregateByKeyword(advid, from, to, cfg.dailyCol(), cfg.advField());
            if (stats.isEmpty()) continue;

            Map<String, String> kwNameMap = buildKwNameMap(advid, cfg, stats);
            Map<String, String> agNameMap = buildNameMap(advid, cfg.agMasterCol(), "gid", "gname");
            Map<String, String> campNameMap = buildNameMap(advid, cfg.campMasterCol(), cfg.campIdField(), cfg.campNameField());

            for (Map<String, Object> stat : stats) {
                Map<String, Object> row = buildRow(stat, kwNameMap, agNameMap, campNameMap, true);
                for (String k : kpis) {
                    allStats.computeIfAbsent(k.trim(), x -> new ArrayList<>()).add(row);
                }
            }
        }

        Map<String, Object> kpisResult = new LinkedHashMap<>();
        for (String k : kpis) {
            String trimmed = k.trim();
            List<Map<String, Object>> rows = allStats.getOrDefault(trimmed, Collections.emptyList());
            rows.sort((a, b) -> {
                Object oa = a.get(trimmed), ob = b.get(trimmed);
                double da = oa instanceof Number n ? n.doubleValue() : 0.0;
                double db = ob instanceof Number n ? n.doubleValue() : 0.0;
                return Double.compare(db, da);
            });
            kpisResult.put(trimmed, rows.size() > 10 ? new ArrayList<>(rows.subList(0, 10)) : rows);
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("result", "success"); res.put("status", "200");
        res.put("data", Map.of("kpis", kpisResult));
        res.put("resultcount", 0); res.put("totalcount", 0);
        return res;
    }

    // ─── 행 빌드 ─────────────────────────────────────────────────────────────

    private Map<String, Object> buildRow(Map<String, Object> stat,
                                          Map<String, String> kwNameMap,
                                          Map<String, String> agNameMap,
                                          Map<String, String> campNameMap,
                                          boolean includeIds) {
        String kwid = (String) stat.get("keyword_id");
        String agid = (String) stat.get("adgroup_id");
        String cid  = (String) stat.get("campaign_id");

        double im  = toDouble(stat,"im"),  clk = toDouble(stat,"clk"),
               cst = toDouble(stat,"cst"), cv  = toDouble(stat,"cv"),
               cr  = toDouble(stat,"cr");

        Map<String, Object> row = new LinkedHashMap<>();
        if (includeIds) {
            row.put("campaign_id",  cid  != null ? cid  : "");
            row.put("adgroup_id",   agid != null ? agid : "");
        }
        row.put("campaign_name",  campNameMap.getOrDefault(cid, ""));
        row.put("adgroup_name",   agNameMap.getOrDefault(agid, ""));
        row.put("keyword_id",     kwid != null ? kwid : "");
        row.put("keyword_name",   kwNameMap.getOrDefault(kwid, ""));
        row.put("keyword_status", "on");
        row.put("im",  Math.round(im));  row.put("clk", Math.round(clk));
        row.put("cst", Math.round(cst)); row.put("cv",  Math.round(cv)); row.put("cr", Math.round(cr));
        row.putAll(calcMetrics(im, clk, cst, cv, cr));
        return row;
    }

    // ─── 마스터 조회 헬퍼 ────────────────────────────────────────────────────

    private Map<String, String> buildKwNameMap(String advid, KwConfig cfg, List<Map<String, Object>> stats) {
        Map<String, String> map = new HashMap<>();
        if (cfg.kwordInDaily()) {
            for (Map<String, Object> s : stats) {
                String kwid  = (String) s.get("keyword_id");
                String kname = (String) s.get("kname");
                if (kwid != null) map.put(kwid, kname != null ? kname : "");
            }
        } else {
            // Naver: naver_keyword master, kwid=keyword_id, kword=keyword_name
            for (Document d : mongoService.findCampaigns(advid, cfg.kwMasterCol())) {
                String kwid  = d.getString("kwid");
                String kword = d.getString("kword");
                if (kwid != null) map.put(kwid, kword != null ? kword : "");
            }
        }
        return map;
    }

    private Map<String, String> buildNameMap(String advid, String collection, String idField, String nameField) {
        Map<String, String> map = new HashMap<>();
        for (Document d : mongoService.findCampaigns(advid, collection)) {
            String id   = d.getString(idField);
            String name = d.getString(nameField);
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
        return switch (md) {
            case "D" -> acc.getAccountKakaosa();
            default  -> acc.getAccountNaverCustomer();
        };
    }

    private void sortRows(List<Map<String, Object>> rows, String sort) {
        String s = sort != null ? sort.trim() : "";
        String field;
        boolean desc;
        if (s.length() >= 2) {
            field = s.substring(0, s.length() - 1);
            desc  = s.charAt(s.length() - 1) == 'd';
        } else {
            field = "cst"; desc = true;
        }
        String f = field;
        rows.sort((x, y) -> {
            Object ox = x.get(f), oy = y.get(f);
            double dx = ox instanceof Number n ? n.doubleValue() : 0.0;
            double dy = oy instanceof Number n ? n.doubleValue() : 0.0;
            return desc ? Double.compare(dy, dx) : Double.compare(dx, dy);
        });
    }

    private Map<String, Object> calcMetrics(double im, double clk, double cst, double cv, double cr) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ctr",  (im > 0 && clk > 0)  ? fmt(clk / im  * 100) : 0);
        m.put("cpc",  (cst > 0 && clk > 0) ? fmt(cst / clk)       : 0);
        m.put("cpa",  (cst > 0 && cv > 0)  ? fmt(cst / cv)        : 0);
        m.put("cvr",  (cv > 0 && clk > 0)  ? fmt(cv  / clk * 100) : 0);
        m.put("roas", (cr > 0 && cst > 0)  ? fmt(cr  / cst * 100) : 0);
        return m;
    }

    private double fmt(double v) { return Math.round(v * 100.0) / 100.0; }

    private double toDouble(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v instanceof Number n ? n.doubleValue() : 0.0;
    }

    private Map<String, Object> fail(String status, String msg) {
        return Map.of("result","failed","status",status,"errormessage",msg);
    }

    private Map<String, Object> noData() {
        return Map.of("result","success","status","1004","errormessage","검색 결과가 없습니다.");
    }
}
