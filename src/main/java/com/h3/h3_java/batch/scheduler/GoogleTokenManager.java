package com.h3.h3_java.batch.scheduler;

import com.h3.h3_java.raw.mongo.GoogleTokenMongoService;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * PHP googletoken.php 이식: 구글 액세스 토큰 관리.
 * MySQL(GoogleMapper) → MongoDB(GoogleTokenMongoService) 전환.
 * 30분마다 verify → 만료 시 refresh_token으로 재발급.
 */
@Slf4j
@Getter
@Component
@RequiredArgsConstructor
public class GoogleTokenManager {

    private final GoogleTokenMongoService tokenMongo;

    @Value("${google.client-id}")
    private String clientId;

    @Value("${google.client-secret}")
    private String clientSecret;

    @Value("${google.redirect-uri}")
    private String redirectUri;

    @Value("${google.developer-token}")
    private String developerToken;

    @Value("${google.manager-id}")
    private String managerId;

    private static final String VERIFY_URL  = "https://oauth2.googleapis.com/tokeninfo?access_token=";
    private static final String REFRESH_URL = "https://oauth2.googleapis.com/token";

    private volatile String accessToken;

    @PostConstruct
    public void init() {
        Document doc = tokenMongo.findToken();
        if (doc == null || doc.getString("access_token") == null) {
            log.warn("[GOOGLE][TOKEN] MongoDB 토큰 없음 - 수동 등록 필요");
            return;
        }
        accessToken = doc.getString("access_token");
        log.info("[GOOGLE][TOKEN] MongoDB 토큰 로드 완료");
    }

    @Scheduled(fixedDelay = 1_800_000, initialDelay = 120_000)
    public void autoRefresh() {
        if (accessToken == null) return;
        if (!isTokenValid(accessToken)) {
            log.info("[GOOGLE][TOKEN] 만료 감지 → 갱신 시작");
            doRefresh();
        }
    }

    public void forceRefresh() {
        doRefresh();
    }

    public String getAccessToken() {
        return accessToken;
    }

    // PHP googletoken.php verify() 와 동일
    private boolean isTokenValid(String token) {
        try {
            SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
            f.setConnectTimeout(10_000);
            f.setReadTimeout(10_000);
            RestTemplate rt = new RestTemplate(f);
            ResponseEntity<String> res = rt.getForEntity(VERIFY_URL + token, String.class);
            return res.getStatusCode().value() == 200;
        } catch (Exception e) {
            log.warn("[GOOGLE][TOKEN][VERIFY] 실패: {}", e.getMessage());
            return false;
        }
    }

    // PHP googletoken.php retoken() 와 동일
    @SuppressWarnings("unchecked")
    void doRefresh() {
        try {
            Document doc = tokenMongo.findToken();
            if (doc == null) { log.warn("[GOOGLE][TOKEN][REFRESH] MongoDB 토큰 없음"); return; }

            String refreshToken = doc.getString("refresh_token");
            if (refreshToken == null || refreshToken.isBlank()) {
                log.warn("[GOOGLE][TOKEN][REFRESH] refresh_token 없음");
                return;
            }

            String formBody = "grant_type=refresh_token"
                + "&client_id="     + clientId
                + "&client_secret=" + clientSecret
                + "&redirect_uri="  + redirectUri
                + "&refresh_token=" + refreshToken;

            SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
            f.setConnectTimeout(15_000);
            f.setReadTimeout(15_000);
            RestTemplate rt = new RestTemplate(f);

            HttpHeaders h = new HttpHeaders();
            h.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            ResponseEntity<String> res = rt.exchange(
                REFRESH_URL, HttpMethod.POST, new HttpEntity<>(formBody, h), String.class);

            if (res.getStatusCode().value() != 200) {
                log.warn("[GOOGLE][TOKEN][REFRESH] HTTP {}: {}", res.getStatusCode(), res.getBody());
                return;
            }

            Map<String, Object> parsed = new com.fasterxml.jackson.databind.ObjectMapper()
                .readValue(res.getBody(), Map.class);
            String newAt = (String) parsed.get("access_token");
            String newRt = parsed.containsKey("refresh_token")
                ? (String) parsed.get("refresh_token")
                : refreshToken;

            if (newAt != null) {
                tokenMongo.saveToken(newAt, newRt);
                accessToken = newAt;
                log.info("[GOOGLE][TOKEN][REFRESH] 갱신 완료");
            }
        } catch (Exception e) {
            log.error("[GOOGLE][TOKEN][REFRESH] 오류: {}", e.getMessage(), e);
        }
    }
}
