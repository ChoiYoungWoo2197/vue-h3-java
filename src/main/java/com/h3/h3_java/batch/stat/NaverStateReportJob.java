package com.h3.h3_java.batch.stat;

import com.h3.h3_java.media.naver.NaverApiClient;
import com.h3.h3_java.media.naver.dto.NaverAccountDto;
import com.h3.h3_java.media.naver.mapper.NaverMasterReportMapper;
import com.h3.h3_java.raw.mongo.NaverStatMongoService;
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
    private final NaverStatMongoService statMongoService;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

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

                Map<String, byte[]> tsvData = createAndDownloadReports(client, customerId, date);

                int kwSaved = processKeywords(customerId, date, tsvData);
                log.info("[NaverStateReport][KEYWORD] customerId={} date={} saved={}", customerId, date, kwSaved);

                // naver_target_daily 수집 비활성화 (타겟팅 페이지 연결 시 재활성화)
                // int tgSaved = processTargets(customerId, date, tsvData);
                // log.info("[NaverStateReport][TARGET] customerId={} date={} saved={}", customerId, date, tgSaved);

            } catch (Exception e) {
                log.error("[NaverStateReport] 오류 customerId={} date={} error={}", customerId, date, e.getMessage(), e);
            }
        }
    }

    // =====================================================================
    // 리포트 생성 → 폴링 → TSV 다운로드
    // =====================================================================

    private Map<String, byte[]> createAndDownloadReports(NaverApiClient client, String customerId, String date) {
        Map<String, byte[]> tsvData = new LinkedHashMap<>();
        String statDt = date.replace("-", "");

        for (String spec : new String[]{"AD_DETAIL", "AD_CONVERSION_DETAIL"}) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("reportTp", spec);
            body.put("statDt",   statDt);

            Map<String, Object> res = client.post("/stat-reports", body);
            String jobId  = str(res != null ? res.get("reportJobId") : null);
            String status = str(res != null ? res.get("status") : null);

            if (jobId.isEmpty() || status.isEmpty()) {
                log.warn("[NaverStateReport] 리포트 생성 실패 customerId={} spec={}", customerId, spec);
                continue;
            }

            Map<String, Object> lastRes = res;
            int maxPolls = 60, polls = 0;
            while (isRunning(status) && polls < maxPolls) {
                sleep(5000);
                lastRes = client.get("/stat-reports/" + jobId);
                status  = lastRes != null ? str(lastRes.get("status")) : "ERROR";
                polls++;
            }

            if ("BUILT".equals(status)) {
                String downloadUrl = lastRes != null ? str(lastRes.get("downloadUrl")) : "";
                if (!downloadUrl.isEmpty()) {
                    byte[] data = client.download(downloadUrl);
                    if (data != null) {
                        tsvData.put(spec, data);
                        log.info("[NaverStateReport] TSV 다운로드 완료 customerId={} spec={} bytes={}", customerId, spec, data.length);
                    }
                }
            } else {
                log.warn("[NaverStateReport] BUILT 아님 customerId={} spec={} status={}", customerId, spec, status);
            }

            client.delete("/stat-reports/" + jobId);
        }
        return tsvData;
    }

    // =====================================================================
    // 키워드 일별 → naver_keyword_daily (MongoDB)
    // TSV에 campaignid/adgroupid가 포함되어 있어 마스터 룩업 없이 직접 수집
    // (마스터 필터링 방식은 마스터 미수집 키워드를 누락시키는 누수 원인)
    // =====================================================================

    private int processKeywords(String customerId, String date, Map<String, byte[]> tsvData) {
        if (tsvData.isEmpty()) return 0;

        Map<String, String>  kwCampaign = new LinkedHashMap<>();
        Map<String, String>  kwAdgroup  = new LinkedHashMap<>();
        Map<String, long[]>  kwStats    = new LinkedHashMap<>(); // [im, clk, cst, cv, cr]

        byte[] adBytes = tsvData.get("AD_DETAIL");
        if (adBytes != null) {
            for (String[] cols : parseTsv(adBytes)) {
                if (cols.length < AD_COLS || !isDateCol(cols[0])) continue;
                String kwId = cols[AD_KWID];
                if ("-".equals(kwId) || kwId.isEmpty()) continue;

                kwCampaign.putIfAbsent(kwId, cols[AD_CAMPAIGNID]);
                kwAdgroup.putIfAbsent(kwId,  cols[AD_ADGROUPID]);
                long[] s = kwStats.computeIfAbsent(kwId, k -> new long[5]);
                s[0] += toLong(cols[AD_IMPRESSION]);
                s[1] += toLong(cols[AD_CLICK]);
                s[2] += (long) Math.floor(toDouble(cols[AD_COST]) * 1.1);
            }
        }

        byte[] cvBytes = tsvData.get("AD_CONVERSION_DETAIL");
        if (cvBytes != null) {
            for (String[] cols : parseTsv(cvBytes)) {
                if (cols.length < CV_COLS || !isDateCol(cols[0])) continue;
                String kwId = cols[CV_KWID];
                if ("-".equals(kwId) || kwId.isEmpty()) continue;

                kwCampaign.putIfAbsent(kwId, cols[CV_CAMPAIGNID]);
                kwAdgroup.putIfAbsent(kwId,  cols[CV_ADGROUPID]);
                long[] s = kwStats.computeIfAbsent(kwId, k -> new long[5]);
                s[3] += toLong(cols[CV_CVCOUNT]);
                s[4] += (long) toDouble(cols[CV_SALESBYCONV]);
            }
        }

        int saved = 0;
        for (Map.Entry<String, long[]> entry : kwStats.entrySet()) {
            long[] s = entry.getValue();
            if (s[0] == 0 && s[1] == 0 && s[2] == 0 && s[3] == 0 && s[4] == 0) continue;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("daily_dt",    date);
            row.put("daily_advid", customerId);
            row.put("campaign_id", kwCampaign.getOrDefault(entry.getKey(), ""));
            row.put("adgroup_id",  kwAdgroup.getOrDefault(entry.getKey(), ""));
            row.put("keyword_id",  entry.getKey());
            row.put("daily_im",    s[0]);
            row.put("daily_clk",   s[1]);
            row.put("daily_cst",   s[2]);
            row.put("daily_cv",    s[3]);
            row.put("daily_cr",    s[4]);

            statMongoService.upsertKeywordDaily(row);
            saved++;
        }
        return saved;
    }

    // =====================================================================
    // 타겟 상세 → naver_target_daily (MongoDB)
    // =====================================================================

    private int processTargets(String customerId, String date, Map<String, byte[]> tsvData) {
        if (tsvData.isEmpty()) return 0;

        List<Map<String, Object>> data1 = new ArrayList<>();
        List<Map<String, Object>> data2 = new ArrayList<>();

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
        int saved = 0, chunkSize = 500;
        for (int i = 0; i < rows.size(); i += chunkSize) {
            List<Map<String, Object>> chunk = rows.subList(i, Math.min(i + chunkSize, rows.size()));
            statMongoService.bulkUpsertTargetRows(chunk);
            saved += chunk.size();
        }
        return saved;
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
            String d = today.minusDays(i).format(DATE_FMT);
            boolean kwMiss = !statMongoService.hasKeywordDailyData(customerId, d);
            // naver_target_daily 비활성화 중 → target gap 체크 제외
            if (kwMiss) dates.add(d);
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

    private boolean isRunning(String status) {
        return "REGIST".equals(status) || "RUNNING".equals(status) || "WAITING".equals(status);
    }

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
