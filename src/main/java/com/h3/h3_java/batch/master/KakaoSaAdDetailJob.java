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
public class KakaoSaAdDetailJob {

    private final KakaoSaMapper             mapper;
    private final KakaoSaMasterMongoService mongoService;
    private final KakaoSaTokenManager       tokenManager;

    public void collect() {
        List<KakaoSaAccountDto> accounts = mapper.selectKakaoSaAccounts();
        log.info("[KAKAO-SA][AD-DETAIL] 전체 수집 시작 accounts={}", accounts.size());
        for (KakaoSaAccountDto account : accounts) {
            collectForAccount(account);
        }
    }

    public boolean collectForUserId(String userId) {
        KakaoSaAccountDto account = mapper.selectKakaoSaAccounts().stream()
            .filter(a -> userId.equals(a.getUserId()))
            .findFirst().orElse(null);
        if (account == null) {
            log.warn("[KAKAO-SA][AD-DETAIL][SKIP] 계정 없음 userId={}", userId);
            return false;
        }
        collectForAccount(account);
        return true;
    }

    @SuppressWarnings("unchecked")
    private void collectForAccount(KakaoSaAccountDto account) {
        String advkey = account.getAccountKakaosa();
        String token  = tokenManager.getAccessToken();

        if (token == null) {
            log.warn("[KAKAO-SA][AD-DETAIL][SKIP] 토큰 없음 advkey={}", advkey);
            return;
        }

        KakaoSaApiClient api = new KakaoSaApiClient(token, advkey);
        log.info("[KAKAO-SA][AD-DETAIL] 시작 advkey={}", advkey);

        // 소재 상세 + 이미지 수집
        List<String> adIds = mongoService.selectAdIds(advkey);
        log.info("[KAKAO-SA][AD-DETAIL] 소재 ads={} advkey={}", adIds.size(), advkey);

        for (String aid : adIds) {
            try {
                Map<String, Object> creative = api.get("/openapi/v1/creatives/basic/" + aid);
                if (creative == null) continue;

                String headline    = str(creative, "title", "");
                String description = str(creative, "description", "");
                String rspvUrl     = "";
                Object landing = creative.get("landingInfo");
                if (landing instanceof Map) {
                    rspvUrl = str((Map<String, Object>) landing, "rspvLandingUrl", "");
                }

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

                Map<String, Object> updates = new HashMap<>();
                updates.put("headline",    headline);
                updates.put("description", description);
                updates.put("purl",        rspvUrl);
                updates.put("purlf",       rspvUrl);
                updates.put("murl",        rspvUrl);
                updates.put("murlf",       rspvUrl);
                updates.put("imgurl1",     imgurl1);
                mongoService.updateAdDetail(aid, updates);
            } catch (Exception e) {
                log.warn("[KAKAO-SA][AD-DETAIL] 소재 상세 조회 실패 aid={} err={}", aid, e.getMessage());
            }
        }

        // 키워드 품질지수 수집
        List<String> kwIds = mongoService.selectKeywordIds(advkey);
        log.info("[KAKAO-SA][AD-DETAIL] 키워드 keywords={} advkey={}", kwIds.size(), advkey);

        for (String kid : kwIds) {
            try {
                Map<String, Object> quality = api.get("/openapi/v1/keywords/" + kid + "/quality");
                if (quality == null) continue;
                int qigrade = 0;
                Object q = quality.get("quality");
                if (q instanceof Number) qigrade = ((Number) q).intValue();
                Map<String, Object> updates = new HashMap<>();
                updates.put("qigrade", qigrade);
                mongoService.updateKeywordDetail(kid, updates);
            } catch (Exception e) {
                log.warn("[KAKAO-SA][AD-DETAIL] 품질지수 조회 실패 kid={} err={}", kid, e.getMessage());
            }
        }

        log.info("[KAKAO-SA][AD-DETAIL] 완료 advkey={} ads={} keywords={}", advkey, adIds.size(), kwIds.size());
    }

    private String str(Map<String, Object> map, String key, String def) {
        Object v = map.get(key);
        return v != null ? String.valueOf(v) : def;
    }

    private String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? String.valueOf(v) : null;
    }
}
