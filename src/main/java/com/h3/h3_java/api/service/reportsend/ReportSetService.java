package com.h3.h3_java.api.service.reportsend;

import com.h3.h3_java.api.service.SendGridService;
import com.h3.h3_java.raw.mongo.ReportMongoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportSetService {

    private final ReportMongoService reportMongoService;
    private final SendGridService    sendGridService;

    private static final DateTimeFormatter DT_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

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

    // ── RESERVATION ──────────────────────────────────────────────────────────

    public Map<String, Object> handleReservation(Map<String, String> params) {
        String mode = params.getOrDefault("mode", "get");
        String bid  = params.getOrDefault("bid", "");
        if (bid.isBlank()) return fail("bid 누락");

        if ("upsert".equals(mode)) {
            return upsertReservation(bid, params);
        }
        return getReservation(bid);
    }

    private Map<String, Object> getReservation(String bid) {
        List<Map<String, Object>> list = reportMongoService.findReservation(bid);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("reportReservationSet", list);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("result",      "success");
        res.put("status",      "200");
        res.put("data",        data);
        res.put("resultcount", 0);
        res.put("totalcount",  0);
        return res;
    }

    private Map<String, Object> upsertReservation(String bid, Map<String, String> params) {
        String userId = params.getOrDefault("userid", "");
        String time   = params.getOrDefault("time",   "");
        String typeRaw = params.getOrDefault("type",  "0");
        int    type    = toInt(typeRaw, 0);

        // semail/remail은 MongoDB h3_report에서 조회
        String semail = "";
        String remail = "";
        try {
            Document report = reportMongoService.findById(bid);
            if (report != null) {
                semail = report.getString("email")  != null ? report.getString("email")  : "";
                remail = report.getString("recver") != null ? report.getString("recver") : "";
            }
        } catch (Exception e) {
            log.warn("[RESERVATION] 리포트 조회 실패 bid={} err={}", bid, e.getMessage());
        }

        reportMongoService.upsertReservation(bid, userId, semail, remail, time, type);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("result", "success");
        res.put("status", "200");
        return res;
    }

    // ── EMAIL SEND ────────────────────────────────────────────────────────────

    public Map<String, Object> handleSendEmail(String userid, String bid, String name,
                                                String sender, String semail,
                                                String recver, String remail,
                                                MultipartFile file) {
        try {
            byte[] pdfBytes = file.getBytes();
            String fileName = file.getOriginalFilename() != null
                    ? file.getOriginalFilename() : "report.pdf";
            String subject  = "희일커뮤니케이션 보고서";
            String htmlBody = "<div style='font-family:Arial,sans-serif;margin-top:60px;'>"
                    + "<p>" + sender + "님으로부터 <strong>" + name + "</strong> 보고서가 도착했습니다.</p>"
                    + "<p>첨부 파일을 확인해 주세요.</p>"
                    + "<p style='color:#999;font-size:11px;'>이 메일은 발신 전용입니다.</p>"
                    + "</div>";

            boolean sent = sendGridService.sendWithPdfAttachment(remail, recver, subject, htmlBody, pdfBytes, fileName);
            log.info("[EMAIL] 보고서 발송 bid={} to={} sent={}", bid, remail, sent);

            if (sent) {
                String sendate = LocalDateTime.now().format(DT_FMT);
                reportMongoService.updateSendStatus(bid, sendate, 1);
            }

            return Map.of("result", "success", "status", "200");
        } catch (Exception e) {
            log.error("[EMAIL] 보고서 발송 오류 bid={} err={}", bid, e.getMessage());
            return fail("발송 오류: " + e.getMessage());
        }
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
