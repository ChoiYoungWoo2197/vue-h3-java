package com.h3.h3_java.batch.stat;

import com.h3.h3_java.batch.scheduler.GoogleTokenManager;
import com.h3.h3_java.media.google.GoogleApiClient;
import com.h3.h3_java.media.google.dto.GoogleAccountDto;
import com.h3.h3_java.media.google.mapper.GoogleMapper;
import com.h3.h3_java.raw.mongo.GoogleMasterMongoService;
import com.h3.h3_java.raw.mongo.GoogleStatMongoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 구글 키워드 일별 수집.
 * PHP googlekeyworddaycollection.php → MongoDB google_keyword_daily.
 * keyword_id = {adgroup_id}-{criterion_id} (복합키)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GoogleKeywordDayJob {

    private final GoogleMapper             mapper;
    private final GoogleTokenManager       tokenManager;
    private final GoogleMasterMongoService masterMongoService;
    private final GoogleStatMongoService   statMongoService;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final long MICROS = 1_000_000L;

    public void collect() {
        List<GoogleAccountDto> accounts = mapper.selectGoogleAccounts();
        log.info("[GOOGLE][KEYWORD-DAY] 전체 수집 시작 accounts={}", accounts.size());
        String token = tokenManager.getAccessToken();
        if (token == null) { log.warn("[GOOGLE][KEYWORD-DAY] 토큰 없음"); return; }
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
        String advkey = account.getAccountGoogle();
        GoogleApiClient api = new GoogleApiClient(token, tokenManager.getDeveloperToken(), tokenManager.getManagerId());

        List<Map<String, Object>> keywords = masterMongoService.findKeywords(advkey);
        if (keywords.isEmpty()) { log.debug("[GOOGLE][KEYWORD-DAY] 키워드 없음 advkey={}", advkey); return; }

        Map<String, Map<String, String>> kidToIds = new HashMap<>();
        for (Map<String, Object> kw : keywords) {
            String kid = str(kw, "kid");
            if (kid == null) continue;
            Map<String, String> ids = new HashMap<>();
            ids.put("campaign_id", str(kw, "campaign_id"));
            ids.put("adgroup_id",  str(kw, "adgroup_id"));
            kidToIds.put(kid, ids);
        }

        for (String date : dates) {
            if (LocalDate.parse(date, FMT).compareTo(LocalDate.now()) >= 0) continue;

            String query = "SELECT ad_group.id, campaign.id, ad_group_criterion.criterion_id, " +
                "ad_group_criterion.keyword.text, metrics.clicks, metrics.conversions, " +
                "metrics.cost_micros, metrics.impressions, metrics.conversions_value " +
                "FROM keyword_view WHERE segments.date = '" + date + "' " +
                "AND ad_group_criterion.status = 'ENABLED'";

            List<Map<String, Object>> rows = api.searchStream(advkey, query);
            int saved = 0;
            for (Map<String, Object> row : rows) {
                Map<String, Object> c = (Map<String, Object>) row.get("campaign");
                Map<String, Object> g = (Map<String, Object>) row.get("adGroup");
                Map<String, Object> k = (Map<String, Object>) row.get("adGroupCriterion");
                Map<String, Object> m = (Map<String, Object>) row.get("metrics");
                if (g == null || k == null || m == null) continue;

                String gid         = str(g, "id");
                String criterionId = str(k, "criterionId");
                if (gid == null || criterionId == null) continue;
                String kid = gid + "-" + criterionId;
                if (!kidToIds.containsKey(kid)) continue;

                Map<String, String> ids = kidToIds.get(kid);
                String cid = c != null ? str(c, "id") : ids.get("campaign_id");

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
                p.put("keyword_id",   kid);
                p.put("daily_im",     im);
                p.put("daily_clk",    clk);
                p.put("daily_cst",    cst);
                p.put("daily_cv",     cv);
                p.put("daily_cr",     cr);
                statMongoService.upsertKeywordDaily(p);
                saved++;
            }
            log.debug("[GOOGLE][KEYWORD-DAY] advkey={} date={} saved={}", advkey, date, saved);
        }
        log.info("[GOOGLE][KEYWORD-DAY] 완료 advkey={} dates={}", advkey, dates.size());
    }

    private List<String> buildAutoDates(String advkey) {
        Set<String> dates = new LinkedHashSet<>();
        LocalDate today = LocalDate.now();
        for (int d : new int[]{1, 3, 5}) dates.add(today.minusDays(d).format(FMT));
        for (int i = 1; i <= 7; i++) {
            String d = today.minusDays(i).format(FMT);
            if (!statMongoService.hasKeywordDaily(advkey, d)) dates.add(d);
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
        return v instanceof Number ? ((Number) v).doubleValue() : 0.0;
    }

    private String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v != null ? String.valueOf(v) : null;
    }
}
