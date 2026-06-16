package com.h3.h3_java.api.service.reportsend;

import com.h3.h3_java.raw.mongo.ReportMongoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class ReportSetService {

    private final ReportMongoService reportMongoService;

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

        int totalcount = reportMongoService.count(userId);
        List<Map<String, Object>> reportset = reportMongoService.find(userId, start * display, display);

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
        String id = params.getOrDefault("id", "-1");

        if (!"-1".equals(id)) {
            // pdfdate 갱신만
            String pdfdate = params.getOrDefault("pdfdate", "");
            reportMongoService.updatePdfDate(id, pdfdate + " 00:00:00");
            return success();
        }

        // 신규 저장
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

        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("user_id",  params.getOrDefault("userid", ""));
        doc.put("name",     params.getOrDefault("name",   ""));
        doc.put("period",   period);
        doc.put("cperiod",  cperiod);
        doc.put("sender",     params.getOrDefault("sender",     ""));
        doc.put("sendate",    params.getOrDefault("sendate",    "0"));
        doc.put("sendstatus", params.getOrDefault("sendstatus", "0"));
        doc.put("pdfdate",    params.getOrDefault("pdfdate",    "0"));
        doc.put("number",     params.getOrDefault("number", ""));
        doc.put("email",      params.getOrDefault("email",  ""));
        doc.put("recver",     params.getOrDefault("recver", ""));
        // media[]: naver=0, kakaosa=1, kakaomo=2, naverda=3, google=4
        doc.put("naver",   get(media, 0, "0"));
        doc.put("kakaosa", get(media, 1, "0"));
        doc.put("kakaomo", get(media, 2, "0"));
        doc.put("naverda", get(media, 3, "0"));
        doc.put("google",  get(media, 4, "0"));
        // page[]: dashboard=0, mediaAnalysis=1, campaignAnalysis=2, periodAnalysis=3,
        //         keywordAnalysis=4, adAnalysis=5, shoppingAnalysis=6
        doc.put("dashboard",        get(page, 0, "0"));
        doc.put("mediaanalysis",    get(page, 1, "0"));
        doc.put("campaignanalysis", get(page, 2, "0"));
        doc.put("periodanalysis",   get(page, 3, "0"));
        doc.put("keywordanalysis",  get(page, 4, "0"));
        doc.put("adanalysis",       get(page, 5, "0"));
        doc.put("shoppinganalysis", get(page, 6, "0"));
        // kpi[]: im=0, clk=1, ctr=2, cpc=3, cst=4, cv=5, cvr=6, cr=7, cpa=8, roas=9, purchase_roas=10
        doc.put("im",            get(kpi,  0, "0"));
        doc.put("clk",           get(kpi,  1, "0"));
        doc.put("ctr",           get(kpi,  2, "0"));
        doc.put("cpc",           get(kpi,  3, "0"));
        doc.put("cst",           get(kpi,  4, "0"));
        doc.put("cv",            get(kpi,  5, "0"));
        doc.put("cvr",           get(kpi,  6, "0"));
        doc.put("cr",            get(kpi,  7, "0"));
        doc.put("cpa",           get(kpi,  8, "0"));
        doc.put("roas",          get(kpi,  9, "0"));
        doc.put("purchase_roas", get(kpi, 10, "0"));
        doc.put("content", params.getOrDefault("content", ""));
        doc.put("daily_regdate", new java.util.Date());

        // s_3: drag 배열 첫 10개 (PHP array_slice($drag, 0, 10) 동일)
        List<String> s3parts = new ArrayList<>();
        for (int i = 0; i < 10; i++) s3parts.add(get(drag, i, "0"));

        Map<String, Object> target = new LinkedHashMap<>();
        target.put("s_1", get(dropdown, 0, "0"));
        target.put("s_2", get(dropdown, 1, "0"));
        target.put("s_3", String.join(",", s3parts));
        doc.put("targets", List.of(target));

        reportMongoService.insert(doc);
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

    private Map<String, Object> success() {
        return Map.of("result", "success", "status", "200");
    }

    private Map<String, Object> fail(String msg) {
        return Map.of("result", "failed", "status", "9999", "errormessage", msg);
    }
}
