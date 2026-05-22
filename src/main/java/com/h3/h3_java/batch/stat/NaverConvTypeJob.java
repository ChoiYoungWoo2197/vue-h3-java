package com.h3.h3_java.batch.stat;

import com.h3.h3_java.media.naver.NaverApiClient;
import com.h3.h3_java.media.naver.dto.NaverAccountDto;
import com.h3.h3_java.media.naver.mapper.NaverConvTypeMapper;
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
public class NaverConvTypeJob {

    private final NaverMasterReportMapper accountMapper;
    private final NaverConvTypeMapper mapper;
    private final NaverStatMongoService statMongoService;

    private static final String REPORT_TYPE = "AD_CONVERSION_DETAIL";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final int CHUNK_SIZE = 500;
    private static final int MAX_POLLS  = 60;

    private static final int CV_CUSTOMERID   = 1;
    private static final int CV_CAMPAIGNID   = 2;
    private static final int CV_ADGROUPID    = 3;
    private static final int CV_ADKEYWORDID  = 4;
    private static final int CV_ADID         = 5;
    private static final int CV_CONVTYPE     = 12;
    private static final int CV_CONVCOUNT    = 13;
    private static final int CV_SALESBYCONV  = 14;
    private static final int CV_COLS         = 15;

    private static final Map<String, String> CONV_TYPE_NAMES;
    static {
        CONV_TYPE_NAMES = new LinkedHashMap<>();
        CONV_TYPE_NAMES.put("purchase",    "구매완료");
        CONV_TYPE_NAMES.put("sign_up",     "회원가입");
        CONV_TYPE_NAMES.put("add_to_cart", "장바구니담기");
        CONV_TYPE_NAMES.put("lead",        "신청완료");
        CONV_TYPE_NAMES.put("custom1",     "사용자정의1");
        CONV_TYPE_NAMES.put("custom2",     "사용자정의2");
        CONV_TYPE_NAMES.put("custom3",     "사용자정의3");
        CONV_TYPE_NAMES.put("custom4",     "사용자정의4");
        CONV_TYPE_NAMES.put("custom5",     "사용자정의5");
        CONV_TYPE_NAMES.put("custom6",     "사용자정의6");
        CONV_TYPE_NAMES.put("custom7",     "사용자정의7");
        CONV_TYPE_NAMES.put("custom8",     "사용자정의8");
        CONV_TYPE_NAMES.put("custom9",     "사용자정의9");
        CONV_TYPE_NAMES.put("custom10",    "사용자정의10");
    }

    public void collect() {
        List<NaverAccountDto> accounts = accountMapper.selectNaverAccounts();
        Set<String> seen = new HashSet<>();
        for (NaverAccountDto acc : accounts) {
            if (shouldSkip(acc.getUserId())) continue;
            String cid = acc.getAccountNaverCustomer();
            if (cid == null || cid.isBlank() || !seen.add(cid)) continue;
            try {
                collectAccount(acc, null, null);
            } catch (Exception e) {
                log.error("[CONVTYPE] 수집 실패 userId={} error={}", acc.getUserId(), e.getMessage(), e);
            }
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
        if (acc == null) {
            log.warn("[CONVTYPE] userId 없음: {}", userId);
            return;
        }
        collectAccount(acc, fromDate, toDate);
    }

    // =====================================================================
    // 계정 단위 처리
    // =====================================================================

    private void collectAccount(NaverAccountDto acc, String fromDate, String toDate) {
        String customerId = acc.getAccountNaverCustomer();
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
                log.info("[CONVTYPE] 시작 customerId={} date={}", customerId, date);
                processDate(client, acc, date);
            } catch (Exception e) {
                log.error("[CONVTYPE] 오류 customerId={} date={} error={}", customerId, date, e.getMessage(), e);
            }
        }
    }

    private void processDate(NaverApiClient client, NaverAccountDto acc, String date) {
        String customerId = acc.getAccountNaverCustomer();
        String userId     = acc.getUserId();
        String statDt     = date.replace("-", "");

        byte[] tsvData = createAndDownloadReport(client, customerId, userId, date, statDt);
        if (tsvData == null) {
            log.warn("[CONVTYPE] 리포트 다운로드 실패 customerId={} date={}", customerId, date);
            return;
        }

        List<String[]> rows = parseTsv(tsvData);

        int cpSaved = processCampaign(customerId, date, rows);
        log.info("[CONVTYPE][CAMPAIGN] customerId={} date={} saved={}", customerId, date, cpSaved);

        int agSaved = processAdgroup(customerId, date, rows);
        log.info("[CONVTYPE][ADGROUP] customerId={} date={} saved={}", customerId, date, agSaved);

        int kwSaved = processKeyword(customerId, date, rows);
        log.info("[CONVTYPE][KEYWORD] customerId={} date={} saved={}", customerId, date, kwSaved);

        int adSaved = processAd(customerId, date, rows);
        log.info("[CONVTYPE][AD] customerId={} date={} saved={}", customerId, date, adSaved);
    }

    // =====================================================================
    // 리포트 생성 → 폴링 → TSV 다운로드
    // =====================================================================

    private byte[] createAndDownloadReport(NaverApiClient client, String customerId, String userId, String date, String statDt) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("reportTp", REPORT_TYPE);
        body.put("statDt",   statDt);

        Map<String, Object> res = client.post("/stat-reports", body);
        String jobId  = str(res != null ? res.get("reportJobId") : null);
        String status = str(res != null ? res.get("status")      : null);

        if (jobId.isEmpty() || status.isEmpty()) {
            log.warn("[CONVTYPE] 리포트 생성 실패 customerId={} date={}", customerId, date);
            insertLog(customerId, date, false, jobId, userId);
            return null;
        }

        Map<String, Object> lastRes = res;
        int polls = 0;
        while (isRunning(status) && polls < MAX_POLLS) {
            sleep(5000);
            lastRes = client.get("/stat-reports/" + jobId);
            status  = lastRes != null ? str(lastRes.get("status")) : "ERROR";
            polls++;
        }

        if (!"BUILT".equals(status)) {
            log.warn("[CONVTYPE] BUILT 아님 customerId={} date={} status={}", customerId, date, status);
            insertLog(customerId, date, false, jobId, userId);
            client.delete("/stat-reports/" + jobId);
            return null;
        }

        if (mapper.countSuccessReport(customerId, date) == 0) {
            insertLog(customerId, date, true, jobId, userId);
        } else {
            log.info("[CONVTYPE] 이미 성공 기록 있음 customerId={} date={}", customerId, date);
        }

        String downloadUrl = lastRes != null ? str(lastRes.get("downloadUrl")) : "";
        byte[] data = null;
        if (!downloadUrl.isEmpty()) {
            data = client.download(downloadUrl);
            if (data != null) {
                log.info("[CONVTYPE] TSV 다운로드 완료 customerId={} date={} bytes={}", customerId, date, data.length);
            }
        }

        client.delete("/stat-reports/" + jobId);
        return data;
    }

    // =====================================================================
    // 캠페인 단위 → naver_campaign_convtype (MongoDB)
    // =====================================================================

    private int processCampaign(String customerId, String date, List<String[]> rows) {
        Map<String, Map<String, Object>> bucket = new LinkedHashMap<>();

        for (String[] cols : rows) {
            if (cols.length < CV_COLS || !isDateCol(cols[0])) continue;

            String dailyAdvid = cols[CV_CUSTOMERID].trim();
            if (dailyAdvid.isEmpty()) dailyAdvid = customerId;
            String campaignId = cols[CV_CAMPAIGNID].trim();
            String convType   = cols[CV_CONVTYPE].trim();

            if (campaignId.isEmpty() || "-".equals(campaignId)) continue;
            if (convType.isEmpty()   || "-".equals(convType))   continue;

            double convCnt   = toDouble(cols[CV_CONVCOUNT]);
            double convValue = toDouble(cols[CV_SALESBYCONV]);

            String key = dailyAdvid + "|" + date + "|" + campaignId + "|" + convType;
            final String fAdvid = dailyAdvid, fCampaign = campaignId, fConvType = convType;
            bucket.computeIfAbsent(key, k -> {
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("daily_advid",    fAdvid);
                r.put("daily_dt",       date);
                r.put("campaign_id",    fCampaign);
                r.put("conv_type_code", fConvType);
                r.put("conv_type_name", convTypeName(fConvType));
                r.put("conv_cnt",   0.0);
                r.put("conv_value", 0.0);
                return r;
            });
            Map<String, Object> r = bucket.get(key);
            r.put("conv_cnt",   (double) r.get("conv_cnt")   + convCnt);
            r.put("conv_value", (double) r.get("conv_value") + convValue);
        }

        if (bucket.isEmpty()) return 0;
        List<Map<String, Object>> list = new ArrayList<>(bucket.values());
        bulkUpsertChunked(list, statMongoService::bulkUpsertCampaignConvType);
        return list.size();
    }

    // =====================================================================
    // 광고그룹 단위 → naver_adgroup_convtype (MongoDB)
    // =====================================================================

    private int processAdgroup(String customerId, String date, List<String[]> rows) {
        Map<String, Map<String, Object>> bucket = new LinkedHashMap<>();

        for (String[] cols : rows) {
            if (cols.length < CV_COLS || !isDateCol(cols[0])) continue;

            String dailyAdvid = cols[CV_CUSTOMERID].trim();
            if (dailyAdvid.isEmpty()) dailyAdvid = customerId;
            String campaignId = cols[CV_CAMPAIGNID].trim();
            String adgroupId  = cols[CV_ADGROUPID].trim();
            String convType   = cols[CV_CONVTYPE].trim();

            if (campaignId.isEmpty() || "-".equals(campaignId)) continue;
            if (adgroupId.isEmpty()  || "-".equals(adgroupId))  continue;
            if (convType.isEmpty()   || "-".equals(convType))   continue;

            double convCnt   = toDouble(cols[CV_CONVCOUNT]);
            double convValue = toDouble(cols[CV_SALESBYCONV]);

            String key = dailyAdvid + "|" + date + "|" + campaignId + "|" + adgroupId + "|" + convType;
            final String fAdvid = dailyAdvid, fCampaign = campaignId, fAdgroup = adgroupId, fConvType = convType;
            bucket.computeIfAbsent(key, k -> {
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("daily_advid",    fAdvid);
                r.put("daily_dt",       date);
                r.put("campaign_id",    fCampaign);
                r.put("adgroup_id",     fAdgroup);
                r.put("conv_type_code", fConvType);
                r.put("conv_type_name", convTypeName(fConvType));
                r.put("conv_cnt",   0.0);
                r.put("conv_value", 0.0);
                return r;
            });
            Map<String, Object> r = bucket.get(key);
            r.put("conv_cnt",   (double) r.get("conv_cnt")   + convCnt);
            r.put("conv_value", (double) r.get("conv_value") + convValue);
        }

        if (bucket.isEmpty()) return 0;
        List<Map<String, Object>> list = new ArrayList<>(bucket.values());
        bulkUpsertChunked(list, statMongoService::bulkUpsertAdGroupConvType);
        return list.size();
    }

    // =====================================================================
    // 키워드 단위 → naver_keyword_convtype (MongoDB)
    // =====================================================================

    private int processKeyword(String customerId, String date, List<String[]> rows) {
        Map<String, Map<String, Object>> bucket = new LinkedHashMap<>();

        for (String[] cols : rows) {
            if (cols.length < CV_COLS || !isDateCol(cols[0])) continue;

            String dailyAdvid = cols[CV_CUSTOMERID].trim();
            if (dailyAdvid.isEmpty()) dailyAdvid = customerId;
            String campaignId = cols[CV_CAMPAIGNID].trim();
            String adgroupId  = cols[CV_ADGROUPID].trim();
            String keywordId  = cols[CV_ADKEYWORDID].trim();
            String convType   = cols[CV_CONVTYPE].trim();

            if (campaignId.isEmpty() || "-".equals(campaignId)) continue;
            if (adgroupId.isEmpty()  || "-".equals(adgroupId))  continue;
            if (keywordId.isEmpty()  || "-".equals(keywordId))  continue;
            if (convType.isEmpty()   || "-".equals(convType))   continue;

            double convCnt   = toDouble(cols[CV_CONVCOUNT]);
            double convValue = toDouble(cols[CV_SALESBYCONV]);

            String key = dailyAdvid + "|" + date + "|" + campaignId + "|" + adgroupId + "|" + keywordId + "|" + convType;
            final String fAdvid = dailyAdvid, fCampaign = campaignId, fAdgroup = adgroupId, fKeyword = keywordId, fConvType = convType;
            bucket.computeIfAbsent(key, k -> {
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("daily_advid",    fAdvid);
                r.put("daily_dt",       date);
                r.put("campaign_id",    fCampaign);
                r.put("adgroup_id",     fAdgroup);
                r.put("keyword_id",     fKeyword);
                r.put("conv_type_code", fConvType);
                r.put("conv_type_name", convTypeName(fConvType));
                r.put("conv_cnt",   0.0);
                r.put("conv_value", 0.0);
                return r;
            });
            Map<String, Object> r = bucket.get(key);
            r.put("conv_cnt",   (double) r.get("conv_cnt")   + convCnt);
            r.put("conv_value", (double) r.get("conv_value") + convValue);
        }

        if (bucket.isEmpty()) return 0;
        List<Map<String, Object>> list = new ArrayList<>(bucket.values());
        bulkUpsertChunked(list, statMongoService::bulkUpsertKeywordConvType);
        return list.size();
    }

    // =====================================================================
    // 소재 단위 → naver_ad_convtype (MongoDB)
    // =====================================================================

    private int processAd(String customerId, String date, List<String[]> rows) {
        Map<String, Map<String, Object>> bucket = new LinkedHashMap<>();

        for (String[] cols : rows) {
            if (cols.length < CV_COLS || !isDateCol(cols[0])) continue;

            String dailyAdvid = cols[CV_CUSTOMERID].trim();
            if (dailyAdvid.isEmpty()) dailyAdvid = customerId;
            String campaignId = cols[CV_CAMPAIGNID].trim();
            String adgroupId  = cols[CV_ADGROUPID].trim();
            String adId       = cols[CV_ADID].trim();
            String convType   = cols[CV_CONVTYPE].trim();

            if (campaignId.isEmpty() || "-".equals(campaignId)) continue;
            if (adgroupId.isEmpty()  || "-".equals(adgroupId))  continue;
            if (adId.isEmpty()       || "-".equals(adId))       continue;
            if (convType.isEmpty()   || "-".equals(convType))   continue;

            double convCnt   = toDouble(cols[CV_CONVCOUNT]);
            double convValue = toDouble(cols[CV_SALESBYCONV]);

            String key = dailyAdvid + "|" + date + "|" + campaignId + "|" + adgroupId + "|" + adId + "|" + convType;
            final String fAdvid = dailyAdvid, fCampaign = campaignId, fAdgroup = adgroupId, fAdId = adId, fConvType = convType;
            bucket.computeIfAbsent(key, k -> {
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("daily_advid",    fAdvid);
                r.put("daily_dt",       date);
                r.put("campaign_id",    fCampaign);
                r.put("adgroup_id",     fAdgroup);
                r.put("ad_id",          fAdId);
                r.put("conv_type_code", fConvType);
                r.put("conv_type_name", convTypeName(fConvType));
                r.put("conv_cnt",   0.0);
                r.put("conv_value", 0.0);
                return r;
            });
            Map<String, Object> r = bucket.get(key);
            r.put("conv_cnt",   (double) r.get("conv_cnt")   + convCnt);
            r.put("conv_value", (double) r.get("conv_value") + convValue);
        }

        if (bucket.isEmpty()) return 0;
        List<Map<String, Object>> list = new ArrayList<>(bucket.values());
        bulkUpsertChunked(list, statMongoService::bulkUpsertAdConvType);
        return list.size();
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
            boolean cpMiss = !statMongoService.hasCampaignConvTypeData(customerId, d);
            boolean agMiss = !statMongoService.hasAdGroupConvTypeData(customerId, d);
            boolean kwMiss = !statMongoService.hasKeywordConvTypeData(customerId, d);
            boolean adMiss = !statMongoService.hasAdConvTypeData(customerId, d);
            if (cpMiss || agMiss || kwMiss || adMiss) dates.add(d);
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

    private void insertLog(String customerId, String date, boolean success, String jobId, String userId) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("deltakey",    customerId);
        row.put("daily_dt",    date);
        row.put("report_type", REPORT_TYPE);
        row.put("success",     success);
        row.put("jobid",       jobId);
        row.put("userid",      userId);
        mapper.insertStatreportLog(row);
    }

    @FunctionalInterface
    private interface BulkUpsert {
        void upsert(List<Map<String, Object>> rows);
    }

    private void bulkUpsertChunked(List<Map<String, Object>> rows, BulkUpsert upsert) {
        for (int i = 0; i < rows.size(); i += CHUNK_SIZE) {
            upsert.upsert(rows.subList(i, Math.min(i + CHUNK_SIZE, rows.size())));
        }
    }

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
            log.error("[CONVTYPE] TSV 파싱 오류: {}", e.getMessage());
        }
        return rows;
    }

    private String convTypeName(String code) {
        return CONV_TYPE_NAMES.getOrDefault(code, code);
    }

    private boolean shouldSkip(String userId) {
        return "admin".equals(userId) || "dydrp123".equals(userId);
    }

    private String str(Object val) {
        return val == null ? "" : String.valueOf(val);
    }

    private double toDouble(String val) {
        if (val == null || val.isEmpty() || "-".equals(val)) return 0.0;
        try { return Double.parseDouble(val.trim()); } catch (Exception e) { return 0.0; }
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
