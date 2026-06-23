package com.h3.h3_java.batch.stat;

import com.h3.h3_java.batch.scheduler.GoogleTokenManager;
import com.h3.h3_java.media.google.GoogleApiClient;
import com.h3.h3_java.media.google.dto.GoogleAccountDto;
import com.h3.h3_java.raw.mongo.AccountMongoService;
import com.h3.h3_java.raw.mongo.AccountLogMongoService;
import com.h3.h3_java.raw.mongo.GoogleMasterMongoService;
import com.h3.h3_java.raw.mongo.GoogleStatMongoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 구글 광고그룹 일별 수집.
 * PHP googleadgroupdaycollection.php → MongoDB google_adgroup_daily.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GoogleAdGroupDayJob {

    private final AccountMongoService      accountMongo;
    private final GoogleTokenManager       tokenManager;
    private final GoogleMasterMongoService masterMongoService;
    private final GoogleStatMongoService   statMongoService;
    private final AccountLogMongoService   accountLogMongo;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final long MICROS = 1_000_000L;

    public void collect() {
        List<GoogleAccountDto> accounts = accountMongo.findGoogleAccountDtos();
        log.info("[GOOGLE][ADGROUP-DAY] 전체 수집 시작 accounts={}", accounts.size());
        String token = tokenManager.getAccessToken();
        if (token == null) { log.warn("[GOOGLE][ADGROUP-DAY] 토큰 없음"); return; }
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
        return accountMongo.findGoogleAccountDtos().stream()
            .filter(a -> userId.equals(a.getUserId())).findFirst().orElse(null);
    }

    @SuppressWarnings("unchecked")
    private void collectForAccount(GoogleAccountDto account, List<String> dates, String token) {
        String advkey = account.getAccountGoogle();
        log.info("[GOOGLE][ADGROUP-DAY] 계정 시작 userId={} advkey={} dates={}", account.getUserId(), advkey, dates);
        GoogleApiClient api = new GoogleApiClient(token, tokenManager.getDeveloperToken(), tokenManager.getManagerId());

        List<Map<String, Object>> adgroups = masterMongoService.findAdGroups(advkey);
        if (adgroups.isEmpty()) { log.debug("[GOOGLE][ADGROUP-DAY] 광고그룹 없음 advkey={}", advkey); return; }

        Map<String, String> gidToCid = new HashMap<>();
        for (Map<String, Object> g : adgroups) gidToCid.put(str(g, "gid"), str(g, "cid"));

        for (String date : dates) {
            if (LocalDate.parse(date, FMT).compareTo(LocalDate.now()) >= 0) continue;

            String query = "SELECT campaign.id, ad_group.id, metrics.clicks, metrics.conversions, " +
                "metrics.cost_micros, metrics.impressions, metrics.conversions_value " +
                "FROM ad_group WHERE segments.date = '" + date + "'";

            List<Map<String, Object>> rows = api.searchStream(advkey, query);
            int saved = 0;
            for (Map<String, Object> row : rows) {
                Map<String, Object> c = (Map<String, Object>) row.get("campaign");
                Map<String, Object> g = (Map<String, Object>) row.get("adGroup");
                Map<String, Object> m = (Map<String, Object>) row.get("metrics");
                if (g == null || m == null) continue;
                String gid = str(g, "id");
                if (gid == null || !gidToCid.containsKey(gid)) continue;
                String cid = c != null ? str(c, "id") : gidToCid.get(gid);

                double im  = doubleVal(m, "impressions");
                double clk = doubleVal(m, "clicks");
                double cst = doubleVal(m, "costMicros") / MICROS;
                double cv  = doubleVal(m, "conversions");
                double cr  = doubleVal(m, "conversionsValue");

                if (im == 0 && clk == 0 && cst == 0 && cv == 0 && cr == 0) continue;

                Map<String, Object> p = new HashMap<>();
                p.put("daily_advid",  advkey);
                p.put("daily_dt",     date);
                p.put("campaign_id",  cid);
                p.put("adgroup_id",   gid);
                p.put("daily_im",     im);
                p.put("daily_clk",    clk);
                p.put("daily_cst",    cst);
                p.put("daily_cv",     cv);
                p.put("daily_cr",     cr);
                statMongoService.upsertAdGroupDaily(p);
                saved++;
            }
            log.info("[GOOGLE][ADGROUP-DAY] date={} saved={} advkey={}", date, saved, advkey);
        }
        log.info("[GOOGLE][ADGROUP-DAY] 완료 advkey={} dates={}", advkey, dates.size());
        accountLogMongo.updateField(advkey, "google", "adgroup");
    }

    private List<String> buildAutoDates(String advkey) {
        Set<String> dates = new LinkedHashSet<>();
        LocalDate today = LocalDate.now();
        for (int d : new int[]{1, 3, 5}) dates.add(today.minusDays(d).format(FMT));
        for (int i = 1; i <= 7; i++) {
            String d = today.minusDays(i).format(FMT);
            if (!statMongoService.hasAdGroupDaily(advkey, d)) dates.add(d);
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

    private String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v != null ? String.valueOf(v) : null;
    }
}
