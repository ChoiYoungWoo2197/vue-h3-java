package com.h3.h3_java.batch.scheduler;

import com.h3.h3_java.raw.mongo.KakaoMoTokenMongoService;
import org.bson.Document;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class KakaoMoTokenManager {

    private final KakaoMoTokenMongoService tokenMongoService;

    @Value("${kakao.mo.client-id}")
    private String clientId;

    @Value("${kakao.mo.client-secret}")
    private String clientSecret;

    private static final String VERIFY_URL  = "https://kapi.kakao.com/v1/user/access_token_info";
    private static final String REFRESH_URL = "https://kauth.kakao.com/oauth/token";

    private volatile String accessToken;

    @PostConstruct
    public void init() {
        Document doc = tokenMongoService.findToken();
        if (doc == null || doc.getString("access_token") == null) {
            log.warn("[KAKAO-MO TOKEN] MongoDB 토큰 없음 - 수동 등록 필요");
            return;
        }
        accessToken = doc.getString("access_token");
        log.info("[KAKAO-MO TOKEN] MongoDB 토큰 로드 완료");
    }

    @Scheduled(fixedDelay = 1_800_000, initialDelay = 90_000)
    public void autoRefresh() {
        if (accessToken == null) return;

        if (!isTokenValid()) {
            log.info("[KAKAO-MO TOKEN] 만료 감지 → 갱신 시작");
            doRefresh();
        }
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void forceRefresh() {
        doRefresh();
    }

    private boolean isTokenValid() {
        try {
            RestTemplate rt = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + accessToken);
            ResponseEntity<Map> res = rt.exchange(
                VERIFY_URL, HttpMethod.GET,
                new HttpEntity<>(headers), Map.class
            );
            return res.getStatusCode().is2xxSuccessful();
        } catch (HttpClientErrorException.Unauthorized e) {
            log.warn("[KAKAO-MO TOKEN] 토큰 만료 (401)");
            return false;
        } catch (Exception e) {
            log.warn("[KAKAO-MO TOKEN] verify 실패: {}", e.getMessage());
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private void doRefresh() {
        try {
            Document doc = tokenMongoService.findToken();
            if (doc == null) return;

            String refreshToken = doc.getString("refresh_token");

            RestTemplate rt = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("grant_type",    "refresh_token");
            body.add("client_id",     clientId);
            body.add("client_secret", clientSecret);
            body.add("refresh_token", refreshToken);

            ResponseEntity<Map> res = rt.postForEntity(
                REFRESH_URL,
                new HttpEntity<>(body, headers),
                Map.class
            );
            Map<String, Object> resBody = res.getBody();

            if (resBody == null || resBody.get("access_token") == null) {
                log.error("[KAKAO-MO TOKEN] 갱신 응답 이상: {}", resBody);
                return;
            }

            String newAt = String.valueOf(resBody.get("access_token"));
            String newRt = resBody.containsKey("refresh_token")
                ? String.valueOf(resBody.get("refresh_token"))
                : refreshToken;

            tokenMongoService.saveToken(newAt, newRt);
            accessToken = newAt;
            log.info("[KAKAO-MO TOKEN] 갱신 완료");

        } catch (Exception e) {
            log.error("[KAKAO-MO TOKEN] 갱신 실패: {}", e.getMessage());
        }
    }
}
