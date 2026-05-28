package com.h3.h3_java.batch.master;

import com.h3.h3_java.batch.scheduler.KakaoSaTokenManager;
import com.h3.h3_java.media.kakao.KakaoSaApiClient;
import com.h3.h3_java.media.kakao.dto.KakaoSaAccountDto;
import com.h3.h3_java.media.kakao.mapper.KakaoSaMapper;
import com.h3.h3_java.raw.mongo.KakaoSaMasterMongoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class KakaoSaMasterJob {

    private final KakaoSaMapper            mapper;
    private final KakaoSaMasterMongoService mongoService;
    private final KakaoSaTokenManager       tokenManager;

    private static final Map<String, Integer> ONOFF = Map.of("ON", 1, "OFF", 0);

    public void collect() {
        List<KakaoSaAccountDto> accounts = mapper.selectKakaoSaAccounts();
        log.info("[KAKAO-SA][MASTER] 전체 수집 시작 accounts={}", accounts.size());
        for (KakaoSaAccountDto account : accounts) {
            collectForAccount(account);
        }
    }

    public boolean collectForUserId(String userId) {
        KakaoSaAccountDto account = mapper.selectKakaoSaAccounts().stream()
            .filter(a -> userId.equals(a.getUserId()))
            .findFirst().orElse(null);
        if (account == null) {
            log.warn("[KAKAO-SA][MASTER][SKIP] 계정 없음 userId={}", userId);
            return false;
        }
        collectForAccount(account);
        return true;
    }

    private void collectForAccount(KakaoSaAccountDto account) {
        String advkey = account.getAccountKakaosa();
        String token  = tokenManager.getAccessToken();

        if (token == null) {
            log.warn("[KAKAO-SA][MASTER][SKIP] 토큰 없음 advkey={}", advkey);
            return;
        }

        KakaoSaApiClient api = new KakaoSaApiClient(token, advkey);
        log.info("[KAKAO-SA][MASTER] 시작 advkey={}", advkey);

        // 1. 캠페인 수집
        List<Map<String, Object>> campaigns = api.getList("/openapi/v1/campaigns", null);
        if (campaigns == null || campaigns.isEmpty()) {
            log.info("[KAKAO-SA][MASTER] 캠페인 없음 advkey={}", advkey);
            return;
        }

        for (Map<String, Object> c : campaigns) {
            String cid   = str(c, "id");
            String cname = str(c, "name");
            String bid   = str(c, "bizChannelId");
            String onoff = str(c, "config");

            if (cid == null) continue;

            Map<String, Object> campaignDoc = new HashMap<>();
            campaignDoc.put("advkey", advkey);
            campaignDoc.put("bid",    bid);
            campaignDoc.put("cid",    cid);
            campaignDoc.put("cname",  cname);
            campaignDoc.put("onoff",  ONOFF.getOrDefault(onoff, 0));
            campaignDoc.put("status", 0);
            mongoService.upsertCampaign(campaignDoc);

            // 2. 광고그룹 수집
            collectAdGroups(api, advkey, cid);
        }

        log.info("[KAKAO-SA][MASTER] 완료 advkey={}", advkey);
    }

    @SuppressWarnings("unchecked")
    private void collectAdGroups(KakaoSaApiClient api, String advkey, String cid) {
        List<Map<String, Object>> groups = api.getList(
            "/openapi/v1/adGroups",
            Map.of("campaignId", cid)
        );
        if (groups == null) return;

        for (Map<String, Object> g : groups) {
            String gid   = str(g, "id");
            String gname = str(g, "name");
            String onoff = str(g, "config");

            if (gid == null) continue;

            Map<String, Object> groupDoc = new HashMap<>();
            groupDoc.put("advkey", advkey);
            groupDoc.put("cid",    cid);
            groupDoc.put("gid",    gid);
            groupDoc.put("gname",  gname);
            groupDoc.put("onoff",  ONOFF.getOrDefault(onoff, 0));
            groupDoc.put("status", 0);
            mongoService.upsertAdGroup(groupDoc);

            // 3. 소재 수집
            collectAds(api, advkey, gid);

            // 4. 키워드 수집
            collectKeywords(api, advkey, gid);
        }
    }

    @SuppressWarnings("unchecked")
    private void collectAds(KakaoSaApiClient api, String advkey, String gid) {
        List<Map<String, Object>> links = api.getList(
            "/openapi/v1/creativeLinks",
            Map.of("adGroupId", gid)
        );
        if (links == null) return;

        for (Map<String, Object> link : links) {
            String lid        = str(link, "creativeLinkId");
            String onoffStr   = str(link, "config");
            String creativeId = str(link, "creativeId");

            if (creativeId == null) continue;

            // 소재 상세 조회
            Map<String, Object> creative = api.get("/openapi/v1/creatives/basic/" + creativeId);
            if (creative == null) continue;

            String headline    = str(creative, "title");
            String description = str(creative, "description");
            String rspvUrl     = "";
            Object landing = creative.get("landingInfo");
            if (landing instanceof Map) {
                Map<String, Object> li = (Map<String, Object>) landing;
                rspvUrl = str(li, "rspvLandingUrl", "");
            }

            // 썸네일 이미지 조회
            String imgurl1 = "";
            Object assets = creative.get("assets");
            if (assets instanceof Map) {
                Object thumbnail = ((Map<String, Object>) assets).get("thumbnail");
                if (thumbnail instanceof Map) {
                    String imageId = str((Map<String, Object>) thumbnail, "imageId");
                    if (imageId != null && !imageId.isEmpty()) {
                        Map<String, Object> img = api.get("/openapi/v1/images/" + imageId);
                        if (img != null) imgurl1 = str(img, "url", "");
                    }
                }
            }

            Map<String, Object> adDoc = new HashMap<>();
            adDoc.put("advkey",      advkey);
            adDoc.put("gid",         gid);
            adDoc.put("type",        1);
            adDoc.put("aid",         creativeId);
            adDoc.put("lid",         lid);
            adDoc.put("headline",    headline);
            adDoc.put("description", description);
            adDoc.put("purl",        rspvUrl);
            adDoc.put("purlf",       rspvUrl);
            adDoc.put("murl",        rspvUrl);
            adDoc.put("murlf",       rspvUrl);
            adDoc.put("onoff",       ONOFF.getOrDefault(onoffStr, 0));
            adDoc.put("imgurl1",     imgurl1);
            adDoc.put("imgurl2",     "");
            adDoc.put("imgurl3",     "");
            mongoService.upsertAd(adDoc);
        }
    }

    @SuppressWarnings("unchecked")
    private void collectKeywords(KakaoSaApiClient api, String advkey, String gid) {
        List<Map<String, Object>> keywords = api.getList(
            "/openapi/v1/keywords",
            Map.of("adGroupId", gid)
        );
        if (keywords == null) return;

        for (Map<String, Object> kw : keywords) {
            String kid   = str(kw, "id");
            String kname = str(kw, "text");
            String onoff = str(kw, "config");
            int bidamount = 0;
            Object bidStrategy = kw.get("bidStrategy");
            if (bidStrategy instanceof Map) {
                Object ba = ((Map<String, Object>) bidStrategy).get("bidAmount");
                if (ba instanceof Number) bidamount = ((Number) ba).intValue();
            }

            if (kid == null) continue;

            // 품질지수 조회
            int qigrade = 0;
            Map<String, Object> quality = api.get("/openapi/v1/keywords/" + kid + "/quality");
            if (quality != null) {
                Object q = quality.get("quality");
                if (q instanceof Number) qigrade = ((Number) q).intValue();
            }

            Map<String, Object> kwDoc = new HashMap<>();
            kwDoc.put("advkey",    advkey);
            kwDoc.put("gid",       gid);
            kwDoc.put("kid",       kid);
            kwDoc.put("kname",     kname);
            kwDoc.put("onoff",     ONOFF.getOrDefault(onoff, 0));
            kwDoc.put("status",    0);
            kwDoc.put("qigrade",   qigrade);
            kwDoc.put("bidamount", bidamount);
            mongoService.upsertKeyword(kwDoc);
        }
    }

    private String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? String.valueOf(v) : null;
    }

    private String str(Map<String, Object> map, String key, String defaultVal) {
        Object v = map.get(key);
        return v != null ? String.valueOf(v) : defaultVal;
    }
}
