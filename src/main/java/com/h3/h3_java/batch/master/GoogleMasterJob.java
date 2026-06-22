package com.h3.h3_java.batch.master;

import com.h3.h3_java.batch.scheduler.GoogleTokenManager;
import com.h3.h3_java.media.google.GoogleApiClient;
import com.h3.h3_java.media.google.dto.GoogleAccountDto;
import com.h3.h3_java.media.google.mapper.GoogleMapper;
import com.h3.h3_java.raw.mongo.GoogleMasterMongoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 구글 마스터 수집.
 * PHP googlemasterreport.php → MongoDB google_campaign / google_adgroup / google_keyword / google_ad.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GoogleMasterJob {

    private final GoogleMapper              mapper;
    private final GoogleTokenManager        tokenManager;
    private final GoogleMasterMongoService  masterMongoService;

    private static final Map<String, Integer> ONOFF = Map.of("ENABLED", 1, "PAUSED", 0, "REMOVED", 99);

    private static final Map<String, Integer> CAMP_TYPE = Map.ofEntries(
        Map.entry("DEMAND_GEN", 1), Map.entry("DISPLAY", 2), Map.entry("HOTEL", 3),
        Map.entry("LOCAL", 4), Map.entry("LOCAL_SERVICES", 5), Map.entry("MULTI_CHANNEL", 6),
        Map.entry("PERFORMANCE_MAX", 7), Map.entry("SEARCH", 8), Map.entry("SHOPPING", 9),
        Map.entry("SMART", 10), Map.entry("TRAVEL", 11), Map.entry("VIDEO", 12)
    );

    private static final Map<String, Integer> BIDGOAL = Map.ofEntries(
        Map.entry("COMMISSION", 1), Map.entry("ENHANCED_CPC", 2), Map.entry("FIXED_CPM", 3),
        Map.entry("INVALID", 4), Map.entry("MANUAL_CPA", 5), Map.entry("MANUAL_CPC", 6),
        Map.entry("MANUAL_CPM", 7), Map.entry("MANUAL_CPV", 8), Map.entry("MAXIMIZE_CONVERSIONS", 9),
        Map.entry("MAXIMIZE_CONVERSION_VALUE", 10), Map.entry("PAGE_ONE_PROMOTED", 11),
        Map.entry("PERCENT_CPC", 12), Map.entry("TARGET_CPA", 13), Map.entry("TARGET_CPM", 14),
        Map.entry("TARGET_CPV", 15), Map.entry("TARGET_IMPRESSION_SHARE", 16),
        Map.entry("TARGET_OUTRANK_SHARE", 17), Map.entry("TARGET_ROAS", 18), Map.entry("TARGET_SPEND", 19)
    );

    private static final Map<String, Integer> AD_TYPE = Map.ofEntries(
        Map.entry("TEXT_AD", 1), Map.entry("RESPONSIVE_SEARCH_AD", 2), Map.entry("CALL_ONLY_AD", 3),
        Map.entry("EXPANDED_DYNAMIC_SEARCH_AD", 4), Map.entry("HOTEL_AD", 5),
        Map.entry("SHOPPING_SMART_AD", 6), Map.entry("SHOPPING_PRODUCT_AD", 7),
        Map.entry("VIDEO_AD", 8), Map.entry("GMAIL_AD", 9), Map.entry("IMAGE_AD", 10),
        Map.entry("RESPONSIVE_DISPLAY_AD", 11), Map.entry("LOCAL_AD", 12), Map.entry("APP_AD", 13),
        Map.entry("DISCOVERY_MULTI_ASSET_AD", 14), Map.entry("DISCOVERY_CAROUSEL_AD", 15),
        Map.entry("PERFORMANCE_MAX_AD", 16), Map.entry("SHOPPING_COMPARISON_LISTING_AD", 17),
        Map.entry("APP_ENGAGEMENT_AD", 18), Map.entry("APP_PRE_REGISTRATION_AD", 19),
        Map.entry("VIDEO_BUMPER_AD", 20), Map.entry("VIDEO_NON_SKIPPABLE_IN_STREAM_AD", 21),
        Map.entry("VIDEO_OUTSTREAM_AD", 22), Map.entry("VIDEO_RESPONSIVE_AD", 23),
        Map.entry("SMART_CAMPAIGN_AD", 24), Map.entry("EXPANDED_TEXT_AD", 25),
        Map.entry("DEMAND_GEN_VIDEO_RESPONSIVE_AD", 26)
    );

    public void collect() {
        List<GoogleAccountDto> accounts = mapper.selectGoogleAccounts();
        log.info("[GOOGLE][MASTER] 전체 수집 시작 accounts={}", accounts.size());
        String token = tokenManager.getAccessToken();
        if (token == null) {
            log.warn("[GOOGLE][MASTER] 토큰 없음");
            return;
        }
        for (GoogleAccountDto account : accounts) {
            collectForAccount(account, token);
        }
    }

    public boolean collectForUserId(String userId) {
        String token = tokenManager.getAccessToken();
        if (token == null) return false;
        GoogleAccountDto account = findAccount(userId);
        if (account == null) return false;
        collectForAccount(account, token);
        return true;
    }

    private GoogleAccountDto findAccount(String userId) {
        return mapper.selectGoogleAccounts().stream()
            .filter(a -> userId.equals(a.getUserId()))
            .findFirst().orElse(null);
    }

    @SuppressWarnings("unchecked")
    private void collectForAccount(GoogleAccountDto account, String token) {
        String advkey = account.getAccountGoogle();
        if (advkey == null || advkey.isBlank()) return;
        log.info("[GOOGLE][MASTER] 계정 시작 userId={} advkey={}", account.getUserId(), advkey);

        GoogleApiClient api = new GoogleApiClient(token, tokenManager.getDeveloperToken(), tokenManager.getManagerId());

        // 1. 캠페인
        List<Map<String, Object>> campaigns = api.searchStream(advkey,
            "SELECT campaign.id, campaign.name, campaign.status, campaign.advertising_channel_type FROM campaign");
        for (Map<String, Object> row : campaigns) {
            Map<String, Object> c = (Map<String, Object>) row.get("campaign");
            if (c == null) continue;
            String cid = str(c, "id");
            if (cid == null) continue;
            Map<String, Object> p = new HashMap<>();
            p.put("advkey", advkey);
            p.put("cid",    cid);
            p.put("cname",  str(c, "name"));
            p.put("type",   CAMP_TYPE.getOrDefault(str(c, "advertisingChannelType"), 99));
            p.put("onoff",  ONOFF.getOrDefault(str(c, "status"), 0));
            masterMongoService.upsertCampaign(p);
        }

        // 2. 광고그룹
        List<Map<String, Object>> adgroups = api.searchStream(advkey,
            "SELECT campaign.id, campaign.bidding_strategy_type, ad_group.id, ad_group.name, ad_group.status FROM ad_group");
        for (Map<String, Object> row : adgroups) {
            Map<String, Object> c = (Map<String, Object>) row.get("campaign");
            Map<String, Object> g = (Map<String, Object>) row.get("adGroup");
            if (c == null || g == null) continue;
            String gid = str(g, "id");
            if (gid == null) continue;
            Map<String, Object> p = new HashMap<>();
            p.put("advkey", advkey); p.put("cid", str(c, "id")); p.put("gid", gid);
            p.put("gname", str(g, "name")); p.put("onoff", ONOFF.getOrDefault(str(g, "status"), 0));
            p.put("bidgoal", BIDGOAL.getOrDefault(str(c, "biddingStrategyType"), 99));
            p.put("bidtype", 0); p.put("budgettype", 0); p.put("budgetamount", 0);
            p.put("bidprice", 0); p.put("pgroups", 0);
            masterMongoService.upsertAdGroup(p);
        }

        // 3. 키워드 (ENABLED만)
        List<Map<String, Object>> keywords = api.searchStream(advkey,
            "SELECT ad_group_criterion.criterion_id, ad_group_criterion.keyword.text, " +
            "ad_group_criterion.status, ad_group_criterion.quality_info.quality_score, ad_group.id " +
            "FROM ad_group_criterion WHERE ad_group_criterion.type = KEYWORD " +
            "AND ad_group_criterion.status = 'ENABLED' AND campaign.status = 'ENABLED'");
        for (Map<String, Object> row : keywords) {
            Map<String, Object> g = (Map<String, Object>) row.get("adGroup");
            Map<String, Object> k = (Map<String, Object>) row.get("adGroupCriterion");
            if (g == null || k == null) continue;
            String gid = str(g, "id");
            String criterionId = str(k, "criterionId");
            if (gid == null || criterionId == null) continue;
            String kid = gid + "-" + criterionId;
            Map<String, Object> kw = (Map<String, Object>) k.get("keyword");
            String kname = kw != null ? str(kw, "text") : "";
            Map<String, Object> qi = (Map<String, Object>) k.get("qualityInfo");
            int qigrade = qi != null ? intVal(qi, "qualityScore") : 0;
            Map<String, Object> p = new HashMap<>();
            p.put("advkey", advkey); p.put("gid", gid); p.put("kid", kid);
            p.put("kname", kname); p.put("onoff", ONOFF.getOrDefault(str(k, "status"), 0));
            p.put("qigrade", qigrade);
            masterMongoService.upsertKeyword(p);
        }

        // 4. 소재 (RESPONSIVE_DISPLAY_AD)
        List<Map<String, Object>> ads = api.searchStream(advkey,
            "SELECT ad_group.id, ad_group_ad.status, ad_group_ad.ad.id, ad_group_ad.ad.type, " +
            "ad_group_ad.ad.final_urls, ad_group_ad.ad.responsive_display_ad.headlines, " +
            "ad_group_ad.ad.responsive_display_ad.descriptions " +
            "FROM ad_group_ad WHERE ad_group_ad.ad.type = 'RESPONSIVE_DISPLAY_AD'");
        for (Map<String, Object> row : ads) {
            Map<String, Object> g   = (Map<String, Object>) row.get("adGroup");
            Map<String, Object> aga = (Map<String, Object>) row.get("adGroupAd");
            if (g == null || aga == null) continue;
            Map<String, Object> ad = (Map<String, Object>) aga.get("ad");
            if (ad == null) continue;
            String gid = str(g, "id");
            String aid = str(ad, "id");
            if (aid == null) continue;
            Map<String, Object> rda = (Map<String, Object>) ad.get("responsiveDisplayAd");
            String headline = "", description = "";
            if (rda != null) {
                List<?> hl = (List<?>) rda.get("headlines");
                if (hl != null && !hl.isEmpty()) { Map<?,?> h0 = (Map<?,?>) hl.get(0); if (h0 != null) { Object v = h0.get("text"); headline = v != null ? String.valueOf(v) : ""; } }
                List<?> dl = (List<?>) rda.get("descriptions");
                if (dl != null && !dl.isEmpty()) { Map<?,?> d0 = (Map<?,?>) dl.get(0); if (d0 != null) { Object v = d0.get("text"); description = v != null ? String.valueOf(v) : ""; } }
            }
            List<?> urls = (List<?>) ad.get("finalUrls");
            String purl = (urls != null && !urls.isEmpty()) ? String.valueOf(urls.get(0)) : "";
            Map<String, Object> p = new HashMap<>();
            p.put("advkey", advkey); p.put("gid", gid); p.put("aid", aid);
            p.put("type", AD_TYPE.getOrDefault(str(ad, "type"), 99));
            p.put("headline", headline); p.put("description", description);
            p.put("purl", purl); p.put("purlf", purl); p.put("murl", purl); p.put("murlf", purl);
            p.put("image_name", ""); p.put("image_url", "");
            p.put("onoff", ONOFF.getOrDefault(str(aga, "status"), 0));
            masterMongoService.upsertAd(p);
        }

        log.info("[GOOGLE][MASTER] 완료 advkey={} campaign={} adgroup={} keyword={} ad={}",
            advkey, campaigns.size(), adgroups.size(), keywords.size(), ads.size());
    }

    private String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v != null ? String.valueOf(v) : null;
    }

    private int intVal(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v instanceof Number ? ((Number) v).intValue() : 0;
    }
}
