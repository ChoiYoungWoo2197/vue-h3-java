package com.h3.h3_java.batch.master;

import com.h3.h3_java.batch.scheduler.KakaoSaTokenManager;
import com.h3.h3_java.media.kakao.KakaoSaApiClient;
import com.h3.h3_java.media.kakao.dto.KakaoSaAccountDto;
import com.h3.h3_java.media.kakao.mapper.KakaoSaMapper;
import com.h3.h3_java.queue.producer.CollectorProducer;
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
    private final CollectorProducer         producer;

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
        String userId  = account.getUserId();
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

        log.info("[KAKAO-SA][MASTER] 완료 advkey={} → ad-detail MQ 발행", advkey);
        // 마스터 완료 후 ad-detail 비동기 수집 트리거
        producer.sendKakaoSaAdDetail(userId);
    }

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

            // 3. 소재 기본 정보만 수집 (상세/이미지는 ad-detail job에서)
            collectAds(api, advkey, gid);

            // 4. 키워드 기본 정보만 수집 (품질지수는 ad-detail job에서)
            collectKeywords(api, advkey, gid);
        }
    }

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

            Map<String, Object> adDoc = new HashMap<>();
            adDoc.put("advkey",      advkey);
            adDoc.put("gid",         gid);
            adDoc.put("type",        1);
            adDoc.put("aid",         creativeId);
            adDoc.put("lid",         lid);
            adDoc.put("headline",    "");
            adDoc.put("description", "");
            adDoc.put("purl",        "");
            adDoc.put("purlf",       "");
            adDoc.put("murl",        "");
            adDoc.put("murlf",       "");
            adDoc.put("onoff",       ONOFF.getOrDefault(onoffStr, 0));
            adDoc.put("imgurl1",     "");
            adDoc.put("imgurl2",     "");
            adDoc.put("imgurl3",     "");
            mongoService.upsertAd(adDoc);
        }
    }

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
                Object ba = ((Map<?, ?>) bidStrategy).get("bidAmount");
                if (ba instanceof Number) bidamount = ((Number) ba).intValue();
            }

            if (kid == null) continue;

            Map<String, Object> kwDoc = new HashMap<>();
            kwDoc.put("advkey",    advkey);
            kwDoc.put("gid",       gid);
            kwDoc.put("kid",       kid);
            kwDoc.put("kname",     kname);
            kwDoc.put("onoff",     ONOFF.getOrDefault(onoff, 0));
            kwDoc.put("status",    0);
            kwDoc.put("qigrade",   0);
            kwDoc.put("bidamount", bidamount);
            mongoService.upsertKeyword(kwDoc);
        }
    }

    private String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? String.valueOf(v) : null;
    }
}
