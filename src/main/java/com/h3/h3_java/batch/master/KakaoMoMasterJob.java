package com.h3.h3_java.batch.master;

import com.h3.h3_java.batch.scheduler.KakaoMoTokenManager;
import com.h3.h3_java.media.kakao.KakaoMoApiClient;
import com.h3.h3_java.media.kakao.dto.KakaoMoAccountDto;
import com.h3.h3_java.media.kakao.mapper.KakaoMoMapper;
import com.h3.h3_java.queue.producer.CollectorProducer;
import com.h3.h3_java.raw.mongo.KakaoMoMasterMongoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class KakaoMoMasterJob {

    private final KakaoMoMapper             mapper;
    private final KakaoMoMasterMongoService mongoService;
    private final KakaoMoTokenManager       tokenManager;
    private final CollectorProducer         producer;

    private static final Map<String, Integer> ONOFF = Map.of("ON", 1, "OFF", 0);

    public void collect() {
        List<KakaoMoAccountDto> accounts = mapper.selectKakaoMoAccounts();
        log.info("[KAKAO-MO][MASTER] 전체 수집 시작 accounts={}", accounts.size());
        for (KakaoMoAccountDto account : accounts) {
            collectForAccount(account);
        }
    }

    public boolean collectForUserId(String userId) {
        KakaoMoAccountDto account = mapper.selectKakaoMoAccounts().stream()
            .filter(a -> userId.equals(a.getUserId()))
            .findFirst().orElse(null);
        if (account == null) {
            log.warn("[KAKAO-MO][MASTER][SKIP] 계정 없음 userId={}", userId);
            return false;
        }
        collectForAccount(account);
        return true;
    }

    private void collectForAccount(KakaoMoAccountDto account) {
        String advkey = account.getAccountKakaomoment();
        String userId  = account.getUserId();
        String token  = tokenManager.getAccessToken();

        if (token == null) {
            log.warn("[KAKAO-MO][MASTER][SKIP] 토큰 없음 advkey={}", advkey);
            return;
        }

        KakaoMoApiClient api = new KakaoMoApiClient(token, advkey);
        log.info("[KAKAO-MO][MASTER] 시작 advkey={}", advkey);

        // 1. 캠페인 수집
        List<Map<String, Object>> campaigns = api.getContent("/openapi/v4/campaigns", null);
        if (campaigns == null || campaigns.isEmpty()) {
            log.info("[KAKAO-MO][MASTER] 캠페인 없음 advkey={}", advkey);
            return;
        }

        for (Map<String, Object> c : campaigns) {
            String cid   = str(c, "id");
            String cname = str(c, "name");
            String onoff = str(c, "config");

            if (cid == null) continue;

            // 캠페인 상세로 type 조회
            String campaignType = "";
            Map<String, Object> detail = api.get("/openapi/v4/campaigns/" + cid);
            if (detail != null) {
                Object typeGoal = detail.get("campaignTypeGoal");
                if (typeGoal instanceof Map) {
                    @SuppressWarnings("unchecked")
                    String ct = str((Map<String, Object>) typeGoal, "campaignType");
                    if (ct != null) campaignType = ct.toLowerCase();
                }
            }

            Map<String, Object> campaignDoc = new HashMap<>();
            campaignDoc.put("advkey", advkey);
            campaignDoc.put("cid",    cid);
            campaignDoc.put("cname",  cname);
            campaignDoc.put("type",   campaignType);
            campaignDoc.put("onoff",  ONOFF.getOrDefault(onoff, 0));
            campaignDoc.put("status", 0);
            mongoService.upsertCampaign(campaignDoc);

            // 2. 광고그룹 수집
            collectAdGroups(api, advkey, cid);
        }

        log.info("[KAKAO-MO][MASTER] 완료 advkey={} → ad-detail MQ 발행", advkey);
        // 마스터 완료 후 ad-detail 비동기 수집 트리거
        producer.sendKakaoMoAdDetail(userId);
    }

    private void collectAdGroups(KakaoMoApiClient api, String advkey, String cid) {
        List<Map<String, Object>> groups = api.getContent(
            "/openapi/v4/adGroups",
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

            // 3. 소재 기본 정보만 수집 (상세/이미지는 ad-detail job에서)
            collectAds(api, advkey, gid);
        }
    }

    private void collectAds(KakaoMoApiClient api, String advkey, String gid) {
        List<Map<String, Object>> creatives = api.getContent(
            "/openapi/v4/creatives",
            Map.of("adGroupId", gid)
        );
        if (creatives == null) return;

        for (Map<String, Object> c : creatives) {
            String aid  = str(c, "id");
            String onoff = str(c, "config");
            if (aid == null) continue;

            Map<String, Object> adDoc = new HashMap<>();
            adDoc.put("advkey",      advkey);
            adDoc.put("gid",         gid);
            adDoc.put("aid",         aid);
            adDoc.put("headline",    "");
            adDoc.put("description", "");
            adDoc.put("purl",        "");
            adDoc.put("purlf",       "");
            adDoc.put("murl",        "");
            adDoc.put("murlf",       "");
            adDoc.put("image_name",  "");
            adDoc.put("image_url",   "");
            adDoc.put("onoff",       ONOFF.getOrDefault(onoff, 0));
            mongoService.upsertAd(adDoc);
        }
    }

    private String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? String.valueOf(v) : null;
    }
}
