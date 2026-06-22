package com.h3.h3_java.batch.master;

import com.h3.h3_java.batch.scheduler.KakaoMoTokenManager;
import com.h3.h3_java.media.kakao.KakaoMoApiClient;
import com.h3.h3_java.media.kakao.dto.KakaoMoAccountDto;
import com.h3.h3_java.media.kakao.mapper.KakaoMoMapper;
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
public class KakaoMoAdDetailJob {

    private final KakaoMoMapper             mapper;
    private final KakaoMoMasterMongoService mongoService;
    private final KakaoMoTokenManager       tokenManager;

    public void collect() {
        List<KakaoMoAccountDto> accounts = mapper.selectKakaoMoAccounts();
        log.info("[KAKAO-MO][AD-DETAIL] 전체 수집 시작 accounts={}", accounts.size());
        for (KakaoMoAccountDto account : accounts) {
            collectForAccount(account);
        }
    }

    public boolean collectForUserId(String userId) {
        KakaoMoAccountDto account = mapper.selectKakaoMoAccounts().stream()
            .filter(a -> userId.equals(a.getUserId()))
            .findFirst().orElse(null);
        if (account == null) {
            log.warn("[KAKAO-MO][AD-DETAIL][SKIP] 계정 없음 userId={}", userId);
            return false;
        }
        collectForAccount(account);
        return true;
    }

    @SuppressWarnings("unchecked")
    private void collectForAccount(KakaoMoAccountDto account) {
        String advkey = account.getAccountKakaomoment();
        String token  = tokenManager.getAccessToken();

        if (token == null) {
            log.warn("[KAKAO-MO][AD-DETAIL][SKIP] 토큰 없음 advkey={}", advkey);
            return;
        }

        KakaoMoApiClient api = new KakaoMoApiClient(token, advkey);

        List<String> adIds = mongoService.selectAdIds(advkey);
        log.info("[KAKAO-MO][AD-DETAIL] 시작 advkey={} ads={}", advkey, adIds.size());

        for (String aid : adIds) {
            try {
                Map<String, Object> detail = api.get("/openapi/v4/creatives/" + aid);
                if (detail == null) continue;

                String headline  = str(detail, "name", "");
                String desc      = str(detail, "description", "");
                String rspvUrl   = str(detail, "rspvLandingUrl", "");
                String format    = str(detail, "format", "");

                String imageUrl  = "";
                String imageName = "";
                if (format.contains("MESSAGE")) {
                    Object msgEl = detail.get("messageElement");
                    if (msgEl instanceof Map) {
                        imageUrl = str((Map<String, Object>) msgEl, "thumbnailUrl", "");
                    }
                } else {
                    Object img = detail.get("image");
                    if (img instanceof Map) {
                        Map<String, Object> imgMap = (Map<String, Object>) img;
                        imageUrl  = str(imgMap, "url", "");
                        imageName = str(imgMap, "fileName", "");
                    }
                }

                Map<String, Object> updates = new HashMap<>();
                updates.put("headline",    headline);
                updates.put("description", desc);
                updates.put("purl",        rspvUrl);
                updates.put("purlf",       rspvUrl);
                updates.put("murl",        rspvUrl);
                updates.put("murlf",       rspvUrl);
                updates.put("image_name",  imageName);
                updates.put("image_url",   imageUrl);
                mongoService.updateAdDetail(aid, updates);
            } catch (Exception e) {
                log.warn("[KAKAO-MO][AD-DETAIL] 소재 상세 조회 실패 aid={} err={}", aid, e.getMessage());
            }
        }

        log.info("[KAKAO-MO][AD-DETAIL] 완료 advkey={} ads={}", advkey, adIds.size());
    }

    private String str(Map<String, Object> map, String key, String def) {
        Object v = map.get(key);
        return v != null ? String.valueOf(v) : def;
    }
}
