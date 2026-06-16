package com.h3.h3_java.api.service.reportsend;

import com.h3.h3_java.api.mapper.ReportMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class ReportSetService {

    private final ReportMapper reportMapper;

    public Map<String, Object> handleReportSet(Map<String, String> params) {
        String mode = params.getOrDefault("mode", "");
        return switch (mode) {
            case "get"    -> getReports(params);
            case "upsert" -> upsertReport(params);
            default       -> fail("invalid mode");
        };
    }

    // ── GET ──────────────────────────────────────────────────────────────────

    private Map<String, Object> getReports(Map<String, String> params) {
        String userId  = params.getOrDefault("userid", "");
        int    start   = toInt(params.get("start"),   0);
        int    display = toInt(params.get("display"), 10);

        int totalcount = reportMapper.countReports(userId);
        List<Map<String, Object>> rows = reportMapper.selectReports(userId, start * display, display);

        List<Map<String, Object>> reportset = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            long seq = toLong(row.get("daily_seq"));
            List<Map<String, Object>> exts = reportMapper.selectReportExts(seq);
            Map<String, Object> r = new LinkedHashMap<>(row);
            r.put("targets", exts);
            reportset.add(r);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("reportset", reportset);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("result",     "success");
        res.put("status",     "200");
        res.put("totalcount", totalcount);
        res.put("data",       data);
        return res;
    }

    // ── UPSERT ───────────────────────────────────────────────────────────────

    private Map<String, Object> upsertReport(Map<String, String> params) {
        long id = toLong(params.getOrDefault("id", "-1"));

        if (id != -1L) {
            String pdfdate = params.getOrDefault("pdfdate", "");
            reportMapper.updatePdfDate(id, pdfdate + " 00:00:00");
            return success();
        }

        String[] kpi      = split(params.get("kpi"));
        String[] media    = split(params.get("media"));
        String[] page     = split(params.get("page"));
        String[] drag     = split(params.get("drag"));
        String[] dropdown = split(params.get("dropdownmenu"));

        String fromdate  = params.getOrDefault("fromdate",  "");
        String todate    = params.getOrDefault("todate",    "");
        String cfromdate = params.getOrDefault("cfromdate", "");
        String ctodate   = params.getOrDefault("ctodate",   "");
        String period    = fromdate + " ~ " + todate;
        String cperiod   = (cfromdate.isBlank() || ctodate.isBlank()) ? null : cfromdate + " ~ " + ctodate;

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("userId",  params.getOrDefault("userid", ""));
        row.put("name",    params.getOrDefault("name",   ""));
        row.put("period",  period);
        row.put("cperiod", cperiod);
        row.put("sender",     params.getOrDefault("sender",     ""));
        row.put("sendate",    params.getOrDefault("sendate",    "0"));
        row.put("sendstatus", params.getOrDefault("sendstatus", "0"));
        row.put("pdfdate",    params.getOrDefault("pdfdate",    "0"));
        row.put("number",     params.getOrDefault("number", ""));
        row.put("email",      params.getOrDefault("email",  ""));
        row.put("recver",     params.getOrDefault("recver", ""));
        // media[]: naver=0, kakaosa=1, kakaomo=2, naverda=3, google=4
        row.put("naver",   get(media, 0, "0"));
        row.put("kakaosa", get(media, 1, "0"));
        row.put("kakaomo", get(media, 2, "0"));
        row.put("naverda", get(media, 3, "0"));
        row.put("google",  get(media, 4, "0"));
        // page[]: dashboard=0, mediaAnalysis=1, campaignAnalysis=2, periodAnalysis=3,
        //         keywordAnalysis=4, adAnalysis=5, shoppingAnalysis=6
        row.put("dashboard",        get(page, 0, "0"));
        row.put("mediaanalysis",    get(page, 1, "0"));
        row.put("campaignanalysis", get(page, 2, "0"));
        row.put("periodanalysis",   get(page, 3, "0"));
        row.put("keywordanalysis",  get(page, 4, "0"));
        row.put("adanalysis",       get(page, 5, "0"));
        row.put("shoppinganalysis", get(page, 6, "0"));
        // kpi[]: im=0, clk=1, ctr=2, cpc=3, cst=4, cv=5, cvr=6, cr=7, cpa=8, roas=9, purchase_roas=10
        row.put("im",            get(kpi,  0, "0"));
        row.put("clk",           get(kpi,  1, "0"));
        row.put("ctr",           get(kpi,  2, "0"));
        row.put("cpc",           get(kpi,  3, "0"));
        row.put("cst",           get(kpi,  4, "0"));
        row.put("cv",            get(kpi,  5, "0"));
        row.put("cvr",           get(kpi,  6, "0"));
        row.put("cr",            get(kpi,  7, "0"));
        row.put("cpa",           get(kpi,  8, "0"));
        row.put("roas",          get(kpi,  9, "0"));
        row.put("purchase_roas", get(kpi, 10, "0"));
        row.put("content", params.getOrDefault("content", ""));

        reportMapper.insertReport(row);
        long newSeq = toLong(row.get("dailySeq"));

        // s_3: first 10 elements of drag array (same as PHP array_slice($drag, 0, 10))
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < 10; i++) parts.add(get(drag, i, "0"));

        Map<String, Object> ext = new LinkedHashMap<>();
        ext.put("bId", newSeq);
        ext.put("s1",  get(dropdown, 0, "0"));
        ext.put("s2",  get(dropdown, 1, "0"));
        ext.put("s3",  String.join(",", parts));
        reportMapper.insertReportExt(ext);

        return success();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private String[] split(String val) {
        if (val == null || val.isBlank()) return new String[0];
        return val.split(",", -1);
    }

    private String get(String[] arr, int idx, String def) {
        return (arr != null && idx < arr.length) ? arr[idx] : def;
    }

    private int toInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }

    private long toLong(Object obj) {
        if (obj == null) return -1L;
        try { return Long.parseLong(obj.toString()); } catch (Exception e) { return -1L; }
    }

    private Map<String, Object> success() {
        return Map.of("result", "success", "status", "200");
    }

    private Map<String, Object> fail(String msg) {
        return Map.of("result", "failed", "status", "9999", "errormessage", msg);
    }
}
