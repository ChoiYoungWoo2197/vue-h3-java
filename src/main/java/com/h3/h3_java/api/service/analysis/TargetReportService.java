package com.h3.h3_java.api.service.analysis;

import com.h3.h3_java.api.dto.AccountDto;
import com.h3.h3_java.raw.mongo.AccountMongoService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TargetReportService {

    private final AccountMongoService accountMongo;
    private final JdbcTemplate jdbc;

    private static final String AGG_COLS =
            " SUM(A.daily_im) AS im, SUM(A.daily_clk) AS clk," +
            " ROUND(SUM(A.daily_cst)) AS cst, SUM(A.daily_cv) AS cv, SUM(A.daily_cr) AS cr," +
            " IFNULL((SUM(A.daily_clk)/SUM(A.daily_im))*100,0) AS ctr," +
            " IFNULL(SUM(A.daily_cst)/SUM(A.daily_clk),0) AS cpc," +
            " IFNULL(SUM(A.daily_cst)/SUM(A.daily_cv),0) AS cpa," +
            " IFNULL((SUM(A.daily_cv)/SUM(A.daily_clk))*100,0) AS cvr," +
            " IFNULL((SUM(A.daily_cr)/SUM(A.daily_cst))*100,0) AS roas";

    private static final Map<String, String> SORT_COL = new HashMap<>();
    static {
        SORT_COL.put("imd","im");  SORT_COL.put("ima","im");
        SORT_COL.put("clkd","clk"); SORT_COL.put("clka","clk");
        SORT_COL.put("cstd","cst"); SORT_COL.put("csta","cst");
        SORT_COL.put("cvd","cv");   SORT_COL.put("cva","cv");
        SORT_COL.put("crd","cr");   SORT_COL.put("cra","cr");
        SORT_COL.put("ctrd","ctr"); SORT_COL.put("ctra","ctr");
        SORT_COL.put("cpcd","cpc"); SORT_COL.put("cpca","cpc");
        SORT_COL.put("cpad","cpa"); SORT_COL.put("cpaa","cpa");
        SORT_COL.put("cvrd","cvr"); SORT_COL.put("cvra","cvr");
        SORT_COL.put("roasd","roas"); SORT_COL.put("roasa","roas");
    }

    // ─── 공개 API ─────────────────────────────────────────────────────────────

    public Map<String, Object> getTargetReport(
            String userId, String fromdate, String todate,
            String md, String level, String target, String id,
            int start, String sort, int display, long totalcount) {

        AccountDto acc = accountMongo.findAccountDtoByUserId(userId);
        if (acc == null) return fail("1009", "계정 없음");

        String advid = advid(acc, md);
        if (advid == null || advid.isBlank()) return noData();

        Map<String, Object> inner;
        switch (level) {
            case "campaign": inner = campaignLevel(md, target, fromdate, todate, advid, sort, start, display, totalcount); break;
            case "adgroup":  inner = adgroupLevel(md, target, fromdate, todate, advid, sort, start, display, totalcount); break;
            case "keyword":  inner = keywordLevel(md, target, fromdate, todate, advid, sort, start, display, totalcount); break;
            case "ad":       inner = adLevel(md, target, fromdate, todate, advid, sort, start, display, totalcount); break;
            default:         return noData();
        }

        List<?> targets = (List<?>) inner.get("targets");
        if (targets == null || targets.isEmpty()) return noData();

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("result", "success");
        resp.put("status", "200");
        resp.put("totalcount", inner.get("totalcount"));
        resp.put("data", Map.of("targets", targets));
        return resp;
    }

    // ─── Campaign level ───────────────────────────────────────────────────────

    private Map<String, Object> campaignLevel(
            String md, String target, String from, String to, String advid,
            String sort, int start, int display, long totalcount) {

        String table = statsTable(md, "campaign");
        if (table.isEmpty()) return emptyInner(0);
        String ob = orderBy(sort, "A.campaign_id ASC");
        int offset = start * display;

        String sql = "SELECT A.campaign_id," + AGG_COLS +
                " FROM " + table + " AS A" +
                " WHERE A.daily_advid=? AND A.daily_dt BETWEEN ? AND ?" +
                " GROUP BY A.campaign_id " + ob + " LIMIT ?,?";
        List<Map<String,Object>> rows = jdbc.queryForList(sql, advid, from, to, offset, display);

        long total = totalcount >= 0 ? totalcount : count(table, "campaign_id", advid, from, to);

        Map<String,String> campNames = nameMap(md, "campaign", advid);
        List<String> ids = rows.stream().map(r -> str(r.get("campaign_id"))).collect(Collectors.toList());
        Map<String, List<Map<String,Object>>> tgts = targetBreakdown(target, advid, from, to, media(md), "campaign", ids);

        List<Map<String,Object>> data = new ArrayList<>();
        int seq = offset;
        for (Map<String,Object> row : rows) {
            String cid = str(row.get("campaign_id"));
            Map<String,Object> item = buildMain(seq++, row);
            item.put("campaignid",    cid);
            item.put("campaigntype",  "");
            item.put("campaign_name", campNames.getOrDefault(cid, ""));
            item.put("adgroup_id",    "");
            item.put("adgroup_name",  "");
            item.put("targets", tgts.getOrDefault(cid, Collections.emptyList()));
            data.add(item);
        }
        return innerResult(data, total);
    }

    // ─── Adgroup level ────────────────────────────────────────────────────────

    private Map<String, Object> adgroupLevel(
            String md, String target, String from, String to, String advid,
            String sort, int start, int display, long totalcount) {

        String table = statsTable(md, "adgroup");
        if (table.isEmpty()) return emptyInner(0);
        String ob = orderBy(sort, "A.adgroup_id ASC");
        int offset = start * display;

        String sql = "SELECT A.adgroup_id, A.campaign_id," + AGG_COLS +
                " FROM " + table + " AS A" +
                " WHERE A.daily_advid=? AND A.daily_dt BETWEEN ? AND ?" +
                " GROUP BY A.adgroup_id, A.campaign_id " + ob + " LIMIT ?,?";
        List<Map<String,Object>> rows = jdbc.queryForList(sql, advid, from, to, offset, display);

        long total = totalcount >= 0 ? totalcount : count(table, "adgroup_id", advid, from, to);

        Map<String,String> campNames = nameMap(md, "campaign", advid);
        Map<String,String> agNames   = nameMap(md, "adgroup", advid);
        List<String> ids = rows.stream().map(r -> str(r.get("adgroup_id"))).collect(Collectors.toList());
        Map<String, List<Map<String,Object>>> tgts = targetBreakdown(target, advid, from, to, media(md), "adgroup", ids);

        List<Map<String,Object>> data = new ArrayList<>();
        int seq = offset;
        for (Map<String,Object> row : rows) {
            String gid = str(row.get("adgroup_id"));
            String cid = str(row.get("campaign_id"));
            Map<String,Object> item = buildMain(seq++, row);
            item.put("campaignid",    cid);
            item.put("campaigntype",  "");
            item.put("campaign_name", campNames.getOrDefault(cid, ""));
            item.put("adgroup_id",    gid);
            item.put("adgroup_name",  agNames.getOrDefault(gid, ""));
            item.put("targets", tgts.getOrDefault(gid, Collections.emptyList()));
            data.add(item);
        }
        return innerResult(data, total);
    }

    // ─── Keyword level ────────────────────────────────────────────────────────

    private Map<String, Object> keywordLevel(
            String md, String target, String from, String to, String advid,
            String sort, int start, int display, long totalcount) {

        if ("K".equals(md)) return emptyInner(0);
        String table = statsTable(md, "keyword");
        if (table.isEmpty()) return emptyInner(0);
        String ob = orderBy(sort, "A.keyword_id ASC");
        int offset = start * display;

        String sql = "SELECT A.keyword_id, A.adgroup_id, A.campaign_id," + AGG_COLS +
                " FROM " + table + " AS A" +
                " WHERE A.daily_advid=? AND A.daily_dt BETWEEN ? AND ?" +
                " GROUP BY A.keyword_id, A.adgroup_id, A.campaign_id " + ob + " LIMIT ?,?";
        List<Map<String,Object>> rows = jdbc.queryForList(sql, advid, from, to, offset, display);

        long total = totalcount >= 0 ? totalcount : count(table, "keyword_id", advid, from, to);

        Map<String,String> campNames = nameMap(md, "campaign", advid);
        Map<String,String> agNames   = nameMap(md, "adgroup", advid);
        Map<String,String> kwNames   = nameMap(md, "keyword", advid);
        List<String> ids = rows.stream().map(r -> str(r.get("keyword_id"))).collect(Collectors.toList());
        Map<String, List<Map<String,Object>>> tgts = targetBreakdown(target, advid, from, to, media(md), "keyword", ids);

        List<Map<String,Object>> data = new ArrayList<>();
        int seq = offset;
        for (Map<String,Object> row : rows) {
            String kid = str(row.get("keyword_id"));
            String gid = str(row.get("adgroup_id"));
            String cid = str(row.get("campaign_id"));
            Map<String,Object> item = buildMain(seq++, row);
            item.put("campaignid",    cid);
            item.put("campaigntype",  "");
            item.put("campaign_name", campNames.getOrDefault(cid, ""));
            item.put("adgroup_id",    gid);
            item.put("adgroup_name",  agNames.getOrDefault(gid, ""));
            item.put("keyword_id",    kid);
            item.put("keyword_name",  kwNames.getOrDefault(kid, ""));
            item.put("targets", tgts.getOrDefault(kid, Collections.emptyList()));
            data.add(item);
        }
        return innerResult(data, total);
    }

    // ─── Ad level ─────────────────────────────────────────────────────────────

    private Map<String, Object> adLevel(
            String md, String target, String from, String to, String advid,
            String sort, int start, int display, long totalcount) {

        String table = statsTable(md, "ad");
        if (table.isEmpty()) return emptyInner(0);
        String ob = orderBy(sort, "A.ad_id ASC");
        int offset = start * display;

        String sql = "SELECT A.ad_id, A.adgroup_id, A.campaign_id," + AGG_COLS +
                " FROM " + table + " AS A" +
                " WHERE A.daily_advid=? AND A.daily_dt BETWEEN ? AND ?" +
                " GROUP BY A.ad_id, A.adgroup_id, A.campaign_id " + ob + " LIMIT ?,?";
        List<Map<String,Object>> rows = jdbc.queryForList(sql, advid, from, to, offset, display);

        long total = totalcount >= 0 ? totalcount : count(table, "ad_id", advid, from, to);

        Map<String,String> campNames = nameMap(md, "campaign", advid);
        Map<String,String> agNames   = nameMap(md, "adgroup", advid);
        Map<String,String> adNames   = nameMap(md, "ad", advid);
        List<String> ids = rows.stream().map(r -> str(r.get("ad_id"))).collect(Collectors.toList());
        Map<String, List<Map<String,Object>>> tgts = targetBreakdown(target, advid, from, to, media(md), "ad", ids);

        List<Map<String,Object>> data = new ArrayList<>();
        int seq = offset;
        for (Map<String,Object> row : rows) {
            String aid = str(row.get("ad_id"));
            String gid = str(row.get("adgroup_id"));
            String cid = str(row.get("campaign_id"));
            Map<String,Object> item = buildMain(seq++, row);
            item.put("campaignid",    cid);
            item.put("campaigntype",  "");
            item.put("campaign_name", campNames.getOrDefault(cid, ""));
            item.put("adgroup_id",    gid);
            item.put("adgroup_name",  agNames.getOrDefault(gid, ""));
            item.put("ad_id",         aid);
            item.put("ad_name",       adNames.getOrDefault(aid, ""));
            item.put("targets", tgts.getOrDefault(aid, Collections.emptyList()));
            data.add(item);
        }
        return innerResult(data, total);
    }

    // ─── Target breakdown ─────────────────────────────────────────────────────

    private Map<String, List<Map<String,Object>>> targetBreakdown(
            String target, String advid, String from, String to,
            String media, String level, List<String> ids) {
        if (ids.isEmpty()) return Collections.emptyMap();
        String tbl = targetTable(target);
        String col = targetCol(target);
        if (tbl == null || col == null) return Collections.emptyMap();

        String ph = ids.stream().map(i -> "?").collect(Collectors.joining(","));
        String sql = "SELECT A.media, A.target_id, A.target_name, A.level, A." + col + " AS target," +
                AGG_COLS +
                " FROM " + tbl + " AS A" +
                " WHERE A.daily_advid=? AND A.daily_dt BETWEEN ? AND ?" +
                " AND A.media=? AND A.level=? AND A.target_id IN (" + ph + ")" +
                " GROUP BY A.target_id, A.level, A." + col;

        List<Object> params = new ArrayList<>(Arrays.asList(advid, from, to, media, level));
        params.addAll(ids);

        List<Map<String,Object>> rows = jdbc.queryForList(sql, params.toArray());
        Map<String, List<Map<String,Object>>> result = new HashMap<>();
        for (Map<String,Object> row : rows) {
            String tid = str(row.get("target_id"));
            Map<String,Object> item = new LinkedHashMap<>();
            item.put("media",       str(row.get("media")));
            item.put("target_id",   tid);
            item.put("target_name", str(row.get("target_name")));
            item.put("level",       str(row.get("level")));
            item.put("target",      str(row.get("target")));
            item.put("im",   toNum(row.get("im")));
            item.put("clk",  toNum(row.get("clk")));
            item.put("cst",  toNum(row.get("cst")));
            item.put("cv",   toNum(row.get("cv")));
            item.put("cr",   toNum(row.get("cr")));
            item.put("ctr",  fmt2(row.get("ctr")));
            item.put("cpc",  fmt2(row.get("cpc")));
            item.put("cpa",  fmt2(row.get("cpa")));
            item.put("cvr",  fmt2(row.get("cvr")));
            item.put("roas", fmt2(row.get("roas")));
            result.computeIfAbsent(tid, k -> new ArrayList<>()).add(item);
        }
        return result;
    }

    // ─── Name lookups ─────────────────────────────────────────────────────────

    private Map<String,String> nameMap(String md, String entity, String advid) {
        try {
            String[] t = nameTableDef(md, entity);
            if (t == null) return Collections.emptyMap();
            Map<String,String> map = new HashMap<>();
            jdbc.queryForList("SELECT " + t[1] + ", " + t[2] + " FROM " + t[0] + " WHERE advkey=?", advid)
                    .forEach(r -> map.put(str(r.get(t[1])), str(r.getOrDefault(t[2], ""))));
            return map;
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private String[] nameTableDef(String md, String entity) {
        switch (entity) {
            case "campaign":
                if ("N".equals(md)) return new String[]{"h3_naver_campaign",           "campaignid", "campaignname"};
                if ("D".equals(md)) return new String[]{"h3_kakaokeyword_campaign",     "cid",        "cname"};
                if ("K".equals(md)) return new String[]{"h3_kakaomoment_campaign",      "cid",        "cname"};
                break;
            case "adgroup":
                if ("N".equals(md)) return new String[]{"h3_naver_adgroup",             "gid",        "gname"};
                if ("D".equals(md)) return new String[]{"h3_kakaokeyword_adgroup",      "gid",        "gname"};
                if ("K".equals(md)) return new String[]{"h3_kakaomoment_adgroup",       "gid",        "gname"};
                break;
            case "keyword":
                if ("N".equals(md)) return new String[]{"h3_naver_keyword",             "kwid",       "kword"};
                if ("D".equals(md)) return new String[]{"h3_kakaokeyword_keyword",      "kid",        "kname"};
                break;
            case "ad":
                if ("N".equals(md)) return new String[]{"h3_naver_ad",                  "adid",       "subject"};
                if ("D".equals(md)) return new String[]{"h3_kakaokeyword_ad",           "aid",        "headline"};
                if ("K".equals(md)) return new String[]{"h3_kakaomoment_ad",            "aid",        "headline"};
                break;
        }
        return null;
    }

    // ─── Table / column mappings ──────────────────────────────────────────────

    private String statsTable(String md, String level) {
        if ("N".equals(md)) {
            switch (level) {
                case "campaign": return "h3_campaign_daily_naver";
                case "adgroup":  return "h3_adgroup_daily_naver";
                case "keyword":  return "h3_keyword_daily_naver_new";
                case "ad":       return "h3_ad_daily_naver";
            }
        } else if ("D".equals(md)) {
            switch (level) {
                case "campaign": return "h3_campaign_daily_kakaokeyword";
                case "adgroup":  return "h3_adgroup_daily_kakaokeyword";
                case "keyword":  return "h3_keyword_daily_kakaokeyword";
                case "ad":       return "h3_ad_daily_kakaokeyword";
            }
        } else if ("K".equals(md)) {
            switch (level) {
                case "campaign": return "h3_campaign_daily_kakaomoment";
                case "adgroup":  return "h3_adgroup_daily_kakaomoment";
                case "ad":       return "h3_ad_daily_kakaomoment";
            }
        }
        return "";
    }

    private String targetTable(String target) {
        switch (target) {
            case "device":   return "h3_target_daily_device";
            case "age":      return "h3_target_daily_age";
            case "gender":   return "h3_target_daily_gender";
            case "location": return "h3_target_daily_location";
            default:         return null;
        }
    }

    private String targetCol(String target) {
        switch (target) {
            case "device":   return "device_type";
            case "age":      return "age_band";
            case "gender":   return "gender";
            case "location": return "location";
            default:         return null;
        }
    }

    private String advid(AccountDto acc, String md) {
        switch (md) {
            case "N": return acc.getAccountNaverCustomer();
            case "D": return acc.getAccountKakaosa();
            case "K": return acc.getAccountKakaomoment();
            default:  return null;
        }
    }

    private String media(String md) {
        switch (md) {
            case "N": return "naver";
            case "D": return "kakaosa";
            case "K": return "kakaomo";
            default:  return "naver";
        }
    }

    private String orderBy(String sort, String second) {
        String col = SORT_COL.getOrDefault(sort != null ? sort : "", "cst");
        String dir = (sort != null && sort.endsWith("a")) ? "ASC" : "DESC";
        return "ORDER BY " + col + " " + dir + ", " + second;
    }

    private long count(String table, String idCol, String advid, String from, String to) {
        Long n = jdbc.queryForObject(
                "SELECT COUNT(DISTINCT " + idCol + ") FROM " + table +
                " WHERE daily_advid=? AND daily_dt BETWEEN ? AND ?",
                Long.class, advid, from, to);
        return n != null ? n : 0L;
    }

    // ─── Row helpers ──────────────────────────────────────────────────────────

    private Map<String,Object> buildMain(int seq, Map<String,Object> row) {
        Map<String,Object> item = new LinkedHashMap<>();
        item.put("daily_seq", seq);
        item.put("im",   toNum(row.get("im")));
        item.put("clk",  toNum(row.get("clk")));
        item.put("cst",  toNum(row.get("cst")));
        item.put("cv",   toNum(row.get("cv")));
        item.put("cr",   toNum(row.get("cr")));
        item.put("ctr",  fmt2(row.get("ctr")));
        item.put("cpc",  fmt2(row.get("cpc")));
        item.put("cpa",  fmt2(row.get("cpa")));
        item.put("cvr",  fmt2(row.get("cvr")));
        item.put("roas", fmt2(row.get("roas")));
        return item;
    }

    private Map<String,Object> innerResult(List<Map<String,Object>> data, long total) {
        Map<String,Object> r = new LinkedHashMap<>();
        r.put("targets",     data);
        r.put("resultcount", 0);
        r.put("totalcount",  total);
        return r;
    }

    private Map<String,Object> emptyInner(long total) {
        Map<String,Object> r = new LinkedHashMap<>();
        r.put("targets",     Collections.emptyList());
        r.put("resultcount", 0);
        r.put("totalcount",  total);
        return r;
    }

    // ─── Utility ──────────────────────────────────────────────────────────────

    private Map<String,Object> fail(String status, String msg) {
        return Map.of("result","failed","status",status,"errormessage",msg);
    }

    private Map<String,Object> noData() {
        return Map.of("result","success","status","1004","errormessage","검색 결과가 없습니다.");
    }

    private String str(Object o) { return o == null ? "" : o.toString(); }

    private double toNum(Object o) {
        if (o == null) return 0;
        if (o instanceof Number) return ((Number) o).doubleValue();
        try { return Double.parseDouble(o.toString()); } catch (Exception e) { return 0; }
    }

    private String fmt2(Object o) { return String.format("%.2f", toNum(o)); }
}
