package com.h3.h3_java.batch.master;

import com.h3.h3_java.batch.scheduler.KakaoSaTokenManager;
import com.h3.h3_java.media.kakao.KakaoSaApiClient;
import com.h3.h3_java.media.kakao.dto.KakaoSaAccountDto;
import com.h3.h3_java.raw.mongo.AccountMongoService;
import com.h3.h3_java.raw.mongo.KakaoSaMasterMongoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class KakaoSaAdDetailJob {

    private final AccountMongoService       accountMongo;
    private final KakaoSaMasterMongoService mongoService;
    private final KakaoSaTokenManager       tokenManager;

    private static final ExecutorService FETCH_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();
    private static final Semaphore       RATE_LIMITER   = new Semaphore(8);

    public void collect() {
        List<KakaoSaAccountDto> accounts = accountMongo.findKakaoSaAccountDtos();
        log.info("[KAKAO-SA][AD-DETAIL] 전체 수집 시작 accounts={}", accounts.size());
        for (KakaoSaAccountDto account : accounts) {
            collectForAccount(account);
        }
    }

    public boolean collectForUserId(String userId) {
        KakaoSaAccountDto account = accountMongo.findKakaoSaAccountDtos().stream()
            .filter(a -> userId.equals(a.getUserId()))
            .findFirst().orElse(null);
        if (account == null) {
            log.warn("[KAKAO-SA][AD-DETAIL][SKIP] 계정 없음 userId={}", userId);
            return false;
        }
        collectForAccount(account);
        return true;
    }

    private void collectForAccount(KakaoSaAccountDto account) {
        String advkey = account.getAccountKakaosa();
        String token  = tokenManager.getAccessToken();

        if (token == null) {
            log.warn("[KAKAO-SA][AD-DETAIL][SKIP] 토큰 없음 advkey={}", advkey);
            return;
        }

        KakaoSaApiClient api = new KakaoSaApiClient(token, advkey);
        log.info("[KAKAO-SA][AD-DETAIL] 시작 advkey={}", advkey);

        processAds(advkey, api);
        processKeywords(advkey, api);

        log.info("[KAKAO-SA][AD-DETAIL] 완료 advkey={}", advkey);
    }

    @SuppressWarnings("unchecked")
    private void processAds(String advkey, KakaoSaApiClient api) {
        List<String> adIds = mongoService.selectAdIds(advkey);
        if (adIds.isEmpty()) {
            log.info("[KAKAO-SA][AD-DETAIL][AD] ads=0 advkey={}", advkey);
            return;
        }
        log.info("[KAKAO-SA][AD-DETAIL][AD] ads={} advkey={}", adIds.size(), advkey);

        ConcurrentHashMap<String, Map<String, Object>> results = new ConcurrentHashMap<>();

        CompletableFuture.allOf(
            adIds.stream()
                .map(aid -> CompletableFuture.runAsync(() -> {
                    try {
                        RATE_LIMITER.acquire();
                        try {
                            Map<String, Object> creative = api.get("/openapi/v1/creatives/basic/" + aid);
                            if (creative == null) return;

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
                                        RATE_LIMITER.acquire();
                                        try {
                                            Map<String, Object> img = api.get("/openapi/v1/images/" + imageId);
                                            if (img != null) imgurl1 = str(img, "url", "");
                                        } finally {
                                            RATE_LIMITER.release();
                                        }
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
                            results.put(aid, updates);
                        } finally {
                            RATE_LIMITER.release();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (Exception e) {
                        log.warn("[KAKAO-SA][AD-DETAIL][AD] 소재 상세 실패 aid={} err={}", aid, e.getMessage());
                    }
                }, FETCH_EXECUTOR))
                .toArray(CompletableFuture[]::new)
        ).join();

        for (Map.Entry<String, Map<String, Object>> entry : results.entrySet()) {
            mongoService.updateAdDetail(entry.getKey(), entry.getValue());
        }
        log.info("[KAKAO-SA][AD-DETAIL][AD] 완료 updated={} advkey={}", results.size(), advkey);
    }

    private void processKeywords(String advkey, KakaoSaApiClient api) {
        List<String> kwIds = mongoService.selectKeywordIds(advkey);
        if (kwIds.isEmpty()) {
            log.info("[KAKAO-SA][AD-DETAIL][KW] keywords=0 advkey={}", advkey);
            return;
        }
        log.info("[KAKAO-SA][AD-DETAIL][KW] keywords={} advkey={}", kwIds.size(), advkey);

        ConcurrentHashMap<String, Integer> kwResults = new ConcurrentHashMap<>();

        CompletableFuture.allOf(
            kwIds.stream()
                .map(kid -> CompletableFuture.runAsync(() -> {
                    try {
                        RATE_LIMITER.acquire();
                        try {
                            Map<String, Object> quality = api.get("/openapi/v1/keywords/" + kid + "/quality");
                            if (quality == null) return;
                            int qigrade = 0;
                            Object q = quality.get("quality");
                            if (q instanceof Number) qigrade = ((Number) q).intValue();
                            kwResults.put(kid, qigrade);
                        } finally {
                            RATE_LIMITER.release();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (Exception e) {
                        log.warn("[KAKAO-SA][AD-DETAIL][KW] 품질지수 실패 kid={} err={}", kid, e.getMessage());
                    }
                }, FETCH_EXECUTOR))
                .toArray(CompletableFuture[]::new)
        ).join();

        for (Map.Entry<String, Integer> entry : kwResults.entrySet()) {
            Map<String, Object> updates = new HashMap<>();
            updates.put("qigrade", entry.getValue());
            mongoService.updateKeywordDetail(entry.getKey(), updates);
        }
        log.info("[KAKAO-SA][AD-DETAIL][KW] 완료 updated={} advkey={}", kwResults.size(), advkey);
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
