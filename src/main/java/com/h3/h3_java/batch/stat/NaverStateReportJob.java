package com.h3.h3_java.batch.stat;

import com.h3.h3_java.media.naver.NaverApiClient;
import com.h3.h3_java.media.naver.dto.NaverAccountDto;
import com.h3.h3_java.media.naver.mapper.NaverMasterReportMapper;
import com.h3.h3_java.media.naver.mapper.NaverStateReportMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class NaverStateReportJob {

    private final NaverMasterReportMapper accountMapper;
    private final NaverStateReportMapper mapper;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // AD_DETAIL 컬럼 인덱스
    private static final int AD_CUSTOMERID  = 1;
    private static final int AD_CAMPAIGNID  = 2;
    private static final int AD_ADGROUPID   = 3;
    private static final int AD_KWID        = 4;
    private static final int AD_ADID        = 5;
    private static final int AD_BCID        = 6;
    private static final int AD_HOURS       = 7;
    private static final int AD_RCODE       = 8;
    private static final int AD_MCODE       = 9;
    private static final int AD_PMTYPE      = 10;
    private static final int AD_IMPRESSION  = 11;
    private static final int AD_CLICK       = 12;
    private static final int AD_COST        = 13;
    private static final int AD_SUMOFRANK   = 14;
    private static final int AD_COLS        = 15;

    // AD_CONVERSION_DETAIL 컬럼 인덱스
    private static final int CV_CUSTOMERID  = 1;
    private static final int CV_CAMPAIGNID  = 2;
    private static final int CV_ADGROUPID   = 3;
    private static final int CV_KWID        = 4;
    private static final int CV_ADID        = 5;
    private static final int CV_BCID        = 6;
    private static final int CV_HOURS       = 7;
    private static final int CV_RCODE       = 8;
    private static final int CV_MCODE       = 9;
    private static final int CV_PMTYPE      = 10;
    private static final int CV_CVMETHOD    = 11;
    private static final int CV_CVTYPE      = 12;
    private static final int CV_CVCOUNT     = 13;
    private static final int CV_SALESBYCONV = 14;
    private static final int CV_COLS        = 15;

    public void collect() {
        List<NaverAccountDto> accounts = accountMapper.selectNaverAccounts();
        Set<String> seen = new HashSet<>();
        for (NaverAccountDto acc : accounts) {
            if (shouldSkip(acc.getUserId())) continue;
            String cid = acc.getAccountNaverCustomer();
            if (cid == null || cid.isBlank() || !seen.add(cid)) continue;
            collectAccount(acc, null, null);
        }
    }

    public boolean collectForUserId(String userId) {
        NaverAccountDto acc = accountMapper.selectNaverAccounts().stream()
            .filter(a -> userId.equals(a.getUserId()))
            .findFirst().orElse(null);
        if (acc == null) return false;
        collectAccount(acc, null, null);
        return true;
    }

    public void collectRange(String userId, String fromDate, String toDate) {
        NaverAccountDto acc = accountMapper.selectNaverAccounts().stream()
            .filter(a -> userId.equals(a.getUserId()))
            .findFirst().orElse(null);
        if (acc == null) return;
        collectAccount(acc, fromDate, toDate);
    }

    private void collectAccount(NaverAccountDto acc, String fromDate, String toDate) {
        String customerId = acc.getAccountNaverCustomer();
        String userId     = acc.getUserId();
        NaverApiClient client = new NaverApiClient(
            acc.getAccountNaverAccess(),
            acc.getAccountNaverSecret(),
            customerId
        );

        List<String> dates = (fromDate != null && toDate != null)
            ? buildDateRange(fromDate, toDate)
            : buildAutoDates(customerId);

        for (String date : dates) {
            try {
                log.info("[NaverStateReport] 시작 customerId={} date={}", customerId, date);

                Map<String, byte[]> tsvData = createAndDownloadReports(client, customerId, userId, date);

                int kwSaved = processKeywords(customerId, date, tsvData);
                log.info("[NaverStateReport][KEYWORD] customerId={} date={} saved={}", customerId, date, kwSaved);

                int tgSaved = processTargets(customerId, date, tsvData);
                log.info("[NaverStateReport][TARGET] customerId={} date={} saved={}", customerId, date, tgSaved);

                deleteReports(client, customerId, date);
            } catch (Exception e) {
                log.error("[NaverStateReport] 오류 customerId={} date={} error={}", customerId, date, e.getMessage(), e);
            }
        }
    }

    // =====================================================================
    // 리포트 생성 → 폴링 → TSV 다운로드 (메모리 보관)
    // =====================================================================

    private Map<String, byte[]> createAndDownloadReports(NaverApiClient client, String customerId, String userId, String date) {
        Map<String, byte[]> tsvData = new LinkedHashMap<>();
        String statDt = date.replace("-", ""); // "20250520"

        for (String spec : new String[]{"AD_DETAIL", "AD_CONVERSION_DETAIL"}) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("reportTp", spec);
            body.put("statDt",   statDt);

            Map<String, Object> res = client.post("/stat-reports", body);
            String jobId = str(res != null ? res.get("reportJobId") : null);
            String status = str(res != null ? res.get("status") : null);

            if (jobId.isEmpty() || status.isEmpty()) {
                log.warn("[NaverStateReport] 리포트 생성 실패 customerId={} spec={}", customerId, spec);
                insertLog(customerId, date, spec, false, jobId, userId);
                continue;
            }

            // BUILT 될 때까지 폴링 (최대 5분)
            Map<String, Object> lastRes = res;
            int maxPolls = 60;
            int polls    = 0;
            while (isRunning(status) && polls < maxPolls) {
                sleep(5000);
                lastRes = client.get("/stat-reports/" + jobId);
                status  = lastRes != null ? str(lastRes.get("status")) : "ERROR";
                polls++;
            }

            if ("BUILT".equals(status)) {
                if (mapper.countSuccessReport(customerId, date, spec) == 0) {
                    insertLog(customerId, date, spec, true, jobId, userId);
                    String downloadUrl = lastRes != null ? str(lastRes.get("downloadUrl")) : "";
                    if (!downloadUrl.isEmpty()) {
                        byte[] data = client.download(downloadUrl);
                        if (data != null) {
                            tsvData.put(spec, data);
                            log.info("[NaverStateReport] TSV 다운로드 완료 customerId={} spec={} bytes={}", customerId, spec, data.length);
                        }
                    }
                } else {
                    log.info("[NaverStateReport] 이미 성공 처리됨 customerId={} spec={} date={}", customerId, spec, date);
                }
            } else {
                log.warn("[NaverStateReport] BUILT 아님 customerId={} spec={} status={}", customerId, spec, status);
                insertLog(customerId, date, spec, false, jobId, userId);
            }

            client.delete("/stat-reports/" + jobId);
        }
        return tsvData;
    }

    // =====================================================================
    // 키워드 일별 집계 → h3_keyword_daily_naver_new UPSERT
    // cost: VAT 10% 포함
    // =====================================================================

    private int processKeywords(String customerId, String date, Map<String, byte[]> tsvData) {
        if (tsvData.isEmpty()) return 0;

        List<Map<String, Object>> kwList = mapper.selectKeywordMapping(customerId);
        if (kwList.isEmpty()) return 0;

        // keywordId -> [campaignId, adgroupId]
        Map<String, String[]> kwMeta  = new LinkedHashMap<>();
        // keywordId -> [im, clk, cst, cv, cr]
        Map<String, long[]>   kwStats = new LinkedHashMap<>();

        for (Map<String, Object> kw : kwList) {
            String kid = str(kw.get("keywordId"));
            if (kid.isEmpty()) continue;
            kwMeta.put(kid,  new String[]{str(kw.get("campaignId")), str(kw.get("adgroupId"))});
            kwStats.put(kid, new long[]{0L, 0L, 0L, 0L, 0L});
        }

        // AD_DETAIL: im, clk, cst (VAT +10%)
        byte[] adBytes = tsvData.get("AD_DETAIL");
        if (adBytes != null) {
            for (String[] cols : parseTsv(adBytes)) {
                if (cols.length < AD_COLS || !isDateCol(cols[0])) continue;
                String kwId = cols[AD_KWID];
                if ("-".equals(kwId) || !kwStats.containsKey(kwId)) continue;
                long[] s = kwStats.get(kwId);
                s[0] += toLong(cols[AD_IMPRESSION]);
                s[1] += toLong(cols[AD_CLICK]);
                s[2] += (long) Math.floor(toDouble(cols[AD_COST]) * 1.1);
            }
        }

        // AD_CONVERSION_DETAIL: cv, cr
        byte[] cvBytes = tsvData.get("AD_CONVERSION_DETAIL");
        if (cvBytes != null) {
            for (String[] cols : parseTsv(cvBytes)) {
                if (cols.length < CV_COLS || !isDateCol(cols[0])) continue;
                String kwId = cols[CV_KWID];
                if ("-".equals(kwId) || !kwStats.containsKey(kwId)) continue;
                long[] s = kwStats.get(kwId);
                s[3] += toLong(cols[CV_CVCOUNT]);
                s[4] += (long) toDouble(cols[CV_SALESBYCONV]);
            }
        }

        int saved = 0;
        for (Map.Entry<String, long[]> entry : kwStats.entrySet()) {
            long[] s = entry.getValue();
            if (s[0] == 0 && s[1] == 0 && s[2] == 0 && s[3] == 0 && s[4] == 0) continue;

            String[]           meta = kwMeta.get(entry.getKey());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("daily_dt",    date);
            row.put("daily_advid", customerId);
            row.put("campaign_id", meta[0]);
            row.put("adgroup_id",  meta[1]);
            row.put("keyword_id",  entry.getKey());
            row.put("daily_im",    s[0]);
            row.put("daily_clk",   s[1]);
            row.put("daily_cst",   s[2]);
            row.put("daily_cv",    s[3]);
            row.put("daily_cr",    s[4]);

            mapper.upsertKeywordDaily(row);
            saved++;
        }
        return saved;
    }

    // =====================================================================
    // 타겟팅 상세 → h3_target_daily_naver UPSERT (bulk)
    // cost: VAT 미적용 (원본 PHP 동일)
    // =====================================================================

    private int processTargets(String customerId, String date, Map<String, byte[]> tsvData) {
        if (tsvData.isEmpty()) return 0;

        List<Map<String, Object>> data1 = new ArrayList<>(); // AD_DETAIL 행
        List<Map<String, Object>> data2 = new ArrayList<>(); // AD_CONVERSION_DETAIL 행

        byte[] adBytes = tsvData.get("AD_DETAIL");
        if (adBytes != null) {
            for (String[] cols : parseTsv(adBytes)) {
                if (cols.length < AD_COLS || !isDateCol(cols[0])) continue;
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("daily_dt",    date);
                r.put("daily_advid", cols[AD_CUSTOMERID].isEmpty() ? customerId : cols[AD_CUSTOMERID]);
                r.put("campaign_id", cols[AD_CAMPAIGNID]);
                r.put("adgroup_id",  cols[AD_ADGROUPID]);
                r.put("keyword_id",  cols[AD_KWID]);
                r.put("ad_id",       cols[AD_ADID]);
                r.put("bc_id",       cols[AD_BCID]);
                r.put("hours",       cols[AD_HOURS]);
                r.put("rcode",       cols[AD_RCODE]);
                r.put("mcode",       cols[AD_MCODE]);
                r.put("pmtype",      cols[AD_PMTYPE]);
                r.put("daily_im",    toLong(cols[AD_IMPRESSION]));
                r.put("daily_clk",   toLong(cols[AD_CLICK]));
                r.put("daily_cst",   toDouble(cols[AD_COST]));
                r.put("daily_cv",    0L);
                r.put("daily_cr",    0.0);
                r.put("sumofrank",   toLong(cols[AD_SUMOFRANK]));
                r.put("cvmethod",    0);
                r.put("cvtype",      "0");
                data1.add(r);
            }
        }

        byte[] cvBytes = tsvData.get("AD_CONVERSION_DETAIL");
        if (cvBytes != null) {
            for (String[] cols : parseTsv(cvBytes)) {
                if (cols.length < CV_COLS || !isDateCol(cols[0])) continue;
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("daily_dt",    date);
                r.put("daily_advid", cols[CV_CUSTOMERID].isEmpty() ? customerId : cols[CV_CUSTOMERID]);
                r.put("campaign_id", cols[CV_CAMPAIGNID]);
                r.put("adgroup_id",  cols[CV_ADGROUPID]);
                r.put("keyword_id",  cols[CV_KWID]);
                r.put("ad_id",       cols[CV_ADID]);
                r.put("bc_id",       cols[CV_BCID]);
                r.put("hours",       cols[CV_HOURS]);
                r.put("rcode",       cols[CV_RCODE]);
                r.put("mcode",       cols[CV_MCODE]);
                r.put("pmtype",      cols[CV_PMTYPE]);
                r.put("daily_im",    0L);
                r.put("daily_clk",   0L);
                r.put("daily_cst",   0.0);
                r.put("daily_cv",    toLong(cols[CV_CVCOUNT]));
                r.put("daily_cr",    toDouble(cols[CV_SALESBYCONV]));
                r.put("sumofrank",   0L);
                r.put("cvmethod",    toInt(cols[CV_CVMETHOD]));
                r.put("cvtype",      cols[CV_CVTYPE]);
                data2.add(r);
            }
        }

        return bulkUpsertChunked(data1) + bulkUpsertChunked(data2);
    }

    private int bulkUpsertChunked(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) return 0;
        int saved     = 0;
        int chunkSize = 500;
        for (int i = 0; i < rows.size(); i += chunkSize) {
            List<Map<String, Object>> chunk = rows.subList(i, Math.min(i + chunkSize, rows.size()));
            mapper.bulkUpsertTargetRows(chunk);
            saved += chunk.size();
        }
        return saved;
    }

    // =====================================================================
    // 리포트 API 삭제 정리
    // =====================================================================

    private void deleteReports(NaverApiClient client, String customerId, String date) {
        List<String> jobIds = mapper.selectJobIdsByDate(customerId, date);
        for (String jobId : jobIds) {
            if (jobId != null && !jobId.isEmpty()) {
                client.delete("/stat-reports/" + jobId);
            }
        }
    }

    // =====================================================================
    // 날짜 목록 생성
    // =====================================================================

    private List<String> buildAutoDates(String customerId) {
        Set<String> dates = new LinkedHashSet<>();
        LocalDate today = LocalDate.now();
        dates.add(today.minusDays(1).format(DATE_FMT));
        dates.add(today.minusDays(3).format(DATE_FMT));
        dates.add(today.minusDays(5).format(DATE_FMT));
        for (int i = 1; i <= 7; i++) {
            String d       = today.minusDays(i).format(DATE_FMT);
            boolean kwMiss = mapper.countKeywordDailyData(customerId, d) == 0;
            boolean tgMiss = mapper.countTargetDailyData(customerId, d)  == 0;
            if (kwMiss || tgMiss) dates.add(d);
        }
        return new ArrayList<>(dates);
    }

    private List<String> buildDateRange(String from, String to) {
        List<String> dates = new ArrayList<>();
        LocalDate cur = LocalDate.parse(from, DATE_FMT);
        LocalDate end = LocalDate.parse(to,   DATE_FMT);
        while (!cur.isAfter(end)) {
            dates.add(cur.format(DATE_FMT));
            cur = cur.plusDays(1);
        }
        return dates;
    }

    // =====================================================================
    // 유틸리티
    // =====================================================================

    private void insertLog(String customerId, String date, String spec, boolean success, String jobId, String userId) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("deltakey",    customerId);
        row.put("daily_dt",    date);
        row.put("report_type", spec);
        row.put("success",     success);
        row.put("jobid",       jobId);
        row.put("userid",      userId);
        mapper.insertStatreportLog(row);
    }

    private boolean isRunning(String status) {
        return "REGIST".equals(status) || "RUNNING".equals(status) || "WAITING".equals(status);
    }

    // TSV 헤더 행 식별 (첫 컬럼이 8자리 날짜인지)
    private boolean isDateCol(String col) {
        return col != null && col.length() == 8 && col.chars().allMatch(Character::isDigit);
    }

    private List<String[]> parseTsv(byte[] data) {
        List<String[]> rows = new ArrayList<>();
        if (data == null || data.length == 0) return rows;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(data), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isEmpty()) rows.add(line.split("\t", -1));
            }
        } catch (Exception e) {
            log.error("[NaverStateReport] TSV 파싱 오류: {}", e.getMessage());
        }
        return rows;
    }

    private boolean shouldSkip(String userId) {
        return "admin".equals(userId) || "dydrp123".equals(userId);
    }

    private String str(Object val) {
        return val == null ? "" : String.valueOf(val);
    }

    private long toLong(String val) {
        if (val == null || val.isEmpty() || "-".equals(val)) return 0L;
        try { return Long.parseLong(val.trim()); } catch (Exception e) { return 0L; }
    }

    private double toDouble(String val) {
        if (val == null || val.isEmpty() || "-".equals(val)) return 0.0;
        try { return Double.parseDouble(val.trim()); } catch (Exception e) { return 0.0; }
    }

    private int toInt(String val) {
        if (val == null || val.isEmpty() || "-".equals(val)) return 0;
        try { return Integer.parseInt(val.trim()); } catch (Exception e) { return 0; }
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
