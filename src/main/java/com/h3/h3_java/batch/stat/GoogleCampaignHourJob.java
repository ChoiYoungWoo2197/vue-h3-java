package com.h3.h3_java.batch.stat;

import com.h3.h3_java.batch.scheduler.GoogleTokenManager;
import com.h3.h3_java.media.google.GoogleApiClient;
import com.h3.h3_java.media.google.dto.GoogleAccountDto;
import com.h3.h3_java.media.google.mapper.GoogleMapper;
import com.h3.h3_java.raw.mongo.AccountLogMongoService;
import com.h3.h3_java.raw.mongo.GoogleStatMongoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 구글 캠페인 시간별 수집.
 * PHP googlecampaignhourcollection.php → MongoDB google_campaign_hour.
 * 모든 캠페인의 hourly 지표를 계정 단위로 합산하여 1행 UPSERT.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GoogleCampaignHourJob {

    private final GoogleMapper           mapper;
    private final GoogleTokenManager     tokenManager;
    private final GoogleStatMongoService statMongoService;
    private final AccountLogMongoService accountLogMongo;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final long MICROS = 1_000_000L;

    public void collect() {
        List<GoogleAccountDto> accounts = mapper.selectGoogleAccounts();
        log.info("[GOOGLE][CAMPAIGN-HOUR] 전체 수집 시작 accounts={}", accounts.size());
        String token = tokenManager.getAccessToken();
        if (token == null) { log.warn("[GOOGLE][CAMPAIGN-HOUR] 토큰 없음"); return; }
        for (GoogleAccountDto account : accounts) {
            String advkey = account.getAccountGoogle();
            if (advkey == null || advkey.isBlank()) continue;
            collectForAccount(account, buildAutoDates(advkey), token);
        }
    }

    public boolean collectForUserId(String userId) {
        String token = tokenManager.getAccessToken();
        if (token == null) return false;
        GoogleAccountDto account = findAccount(userId);
        if (account == null) return false;
        collectForAccount(account, buildAutoDates(account.getAccountGoogle()), token);
        return true;
    }

    public boolean collectRange(String userId, String fromDate, String toDate) {
        String token = tokenManager.getAccessToken();
        if (token == null) return false;
        GoogleAccountDto account = findAccount(userId);
        if (account == null) return false;
        collectForAccount(account, buildDateRange(fromDate, toDate), token);
        return true;
    }

    private GoogleAccountDto findAccount(String userId) {
        return mapper.selectGoogleAccounts().stream()
            .filter(a -> userId.equals(a.getUserId())).findFirst().orElse(null);
    }

    @SuppressWarnings("unchecked")
    private void collectForAccount(GoogleAccountDto account, List<String> dates, String token) {
        String advkey  = account.getAccountGoogle();
        String userId  = account.getUserId();
        log.info("[GOOGLE][CAMPAIGN-HOUR] 계정 시작 userId={} advkey={} dates={}", userId, advkey, dates);
        GoogleApiClient api = new GoogleApiClient(token, tokenManager.getDeveloperToken(), tokenManager.getManagerId());

        for (String date : dates) {
            if (LocalDate.parse(date, FMT).compareTo(LocalDate.now()) >= 0) continue;

            String query = "SELECT campaign.id, segments.hour, metrics.clicks, metrics.conversions, " +
                "metrics.cost_micros, metrics.impressions, metrics.conversions_value " +
                "FROM campaign WHERE segments.date = '" + date + "' ORDER BY segments.hour ASC";

            List<Map<String, Object>> rows = api.searchStream(advkey, query);

            // 계정 단위 합산: im/clk/cst/cv/cr × 24시간
            double[] im  = new double[24];
            double[] clk = new double[24];
            double[] cst = new double[24];
            double[] cv  = new double[24];
            double[] cr  = new double[24];

            for (Map<String, Object> row : rows) {
                Map<String, Object> seg = (Map<String, Object>) row.get("segments");
                Map<String, Object> m   = (Map<String, Object>) row.get("metrics");
                if (seg == null || m == null) continue;
                int h = intVal(seg, "hour");
                if (h < 0 || h > 23) continue;
                im[h]  += doubleVal(m, "impressions");
                clk[h] += doubleVal(m, "clicks");
                cst[h] += doubleVal(m, "costMicros") / MICROS;
                cv[h]  += doubleVal(m, "conversions");
                cr[h]  += doubleVal(m, "conversionsValue");
            }

            double sum = 0;
            for (int h = 0; h < 24; h++) sum += im[h] + clk[h] + cst[h] + cv[h] + cr[h];
            if (sum <= 0) continue;

            Map<String, Object> p = new HashMap<>();
            p.put("user_id", userId);
            p.put("adv_id",  advkey);
            p.put("hour_dt", date);
            for (int h = 0; h < 24; h++) {
                String hh = String.format("%02d", h);
                p.put("hour_" + hh + "_im",  im[h]);
                p.put("hour_" + hh + "_clk", clk[h]);
                p.put("hour_" + hh + "_cst", cst[h]);
                p.put("hour_" + hh + "_cv",  cv[h]);
                p.put("hour_" + hh + "_cr",  cr[h]);
            }
            statMongoService.upsertCampaignHour(p);
            log.info("[GOOGLE][CAMPAIGN-HOUR] date={} saved={} advkey={}", date, 1, advkey);
        }
        log.info("[GOOGLE][CAMPAIGN-HOUR] 완료 advkey={} dates={}", advkey, dates.size());
        accountLogMongo.updateField(advkey, "google", "campaign_hour");
    }

    private List<String> buildAutoDates(String advkey) {
        Set<String> dates = new LinkedHashSet<>();
        LocalDate today = LocalDate.now();
        for (int d : new int[]{1, 3, 5}) dates.add(today.minusDays(d).format(FMT));
        for (int i = 1; i <= 7; i++) {
            String d = today.minusDays(i).format(FMT);
            if (!statMongoService.hasCampaignHour(advkey, d)) dates.add(d);
        }
        return new ArrayList<>(dates);
    }

    private List<String> buildDateRange(String from, String to) {
        List<String> dates = new ArrayList<>();
        LocalDate cur = LocalDate.parse(from, FMT), end = LocalDate.parse(to, FMT);
        while (!cur.isAfter(end)) { dates.add(cur.format(FMT)); cur = cur.plusDays(1); }
        return dates;
    }

    private double doubleVal(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v instanceof Number) return ((Number) v).doubleValue();
        if (v instanceof String) try { return Double.parseDouble((String) v); } catch (Exception e) { return 0.0; }
        return 0.0;
    }

    private int intVal(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v instanceof Number ? ((Number) v).intValue() : 0;
    }
}
