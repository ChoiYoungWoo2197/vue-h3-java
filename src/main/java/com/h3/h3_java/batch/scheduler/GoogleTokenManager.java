package com.h3.h3_java.batch.scheduler;

import com.h3.h3_java.media.google.mapper.GoogleMapper;
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
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Slf4j
@Getter
@Component
@RequiredArgsConstructor
public class GoogleTokenManager {

    private final GoogleMapper            mapper;
    private final GoogleTokenMongoService mongoService;

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

    private volatile String accessToken;

    @PostConstruct
    public void init() {
        Document token = mongoService.findToken();
        if (token == null) {
            log.info("[GOOGLE TOKEN] MongoDB 토큰 없음 → MySQL 마이그레이션 시도");
            migrateFromMySQL();
        } else {
            accessToken = token.getString("access_token");
            log.info("[GOOGLE TOKEN] MongoDB 토큰 로드 완료");
        }
    }

    @Scheduled(fixedDelay = 1_800_000, initialDelay = 120_000)
    public void autoRefresh() {
        if (accessToken == null) return;
        if (!isTokenValid()) {
            log.info("[GOOGLE TOKEN] 만료 감지 → 갱신 시작");
            doRefresh();
        }
    }

    public String getAccessToken() {
        return accessToken;
    }

    private void migrateFromMySQL() {
        try {
            String at = mapper.selectGoogleAccessToken();
            String rt = mapper.selectGoogleRefreshToken();
            if (at == null || at.isBlank()) {
                log.warn("[GOOGLE TOKEN] MySQL에도 토큰 없음 - 수동 등록 필요");
                return;
            }
            mongoService.saveToken(at, rt != null ? rt : "");
            accessToken = at;
            log.info("[GOOGLE TOKEN] MySQL → MongoDB 마이그레이션 완료");
        } catch (Exception e) {
            log.error("[GOOGLE TOKEN] 마이그레이션 실패: {}", e.getMessage());
        }
    }

    private boolean isTokenValid() {
        try {
            SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
            f.setConnectTimeout(10_000);
            f.setReadTimeout(10_000);
            RestTemplate rt = new RestTemplate(f);
            ResponseEntity<String> res = rt.getForEntity(
                "https://oauth2.googleapis.com/tokeninfo?access_token=" + accessToken, String.class);
            return res.getStatusCode().value() == 200;
        } catch (Exception e) {
            log.warn("[GOOGLE TOKEN][VERIFY] 실패: {}", e.getMessage());
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private void doRefresh() {
        try {
            Document current = mongoService.findToken();
            if (current == null) return;

            String refreshToken = current.getString("refresh_token");
            if (refreshToken == null || refreshToken.isBlank()) {
                log.warn("[GOOGLE TOKEN][REFRESH] refresh_token 없음");
                return;
            }

            SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
            f.setConnectTimeout(15_000);
            f.setReadTimeout(15_000);
            RestTemplate rt = new RestTemplate(f);

            HttpHeaders h = new HttpHeaders();
            h.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("grant_type",    "refresh_token");
            body.add("client_id",     clientId);
            body.add("client_secret", clientSecret);
            body.add("redirect_uri",  redirectUri);
            body.add("refresh_token", refreshToken);

            ResponseEntity<Map> res = rt.exchange(
                "https://oauth2.googleapis.com/token",
                HttpMethod.POST,
                new HttpEntity<>(body, h),
                Map.class
            );

            Map<String, Object> resBody = res.getBody();
            if (resBody == null || resBody.get("access_token") == null) {
                log.error("[GOOGLE TOKEN][REFRESH] 갱신 응답 이상: {}", resBody);
                return;
            }

            String newAt = String.valueOf(resBody.get("access_token"));
            String newRt = resBody.containsKey("refresh_token")
                ? String.valueOf(resBody.get("refresh_token"))
                : refreshToken;

            mongoService.saveToken(newAt, newRt);
            accessToken = newAt;
            log.info("[GOOGLE TOKEN][REFRESH] 갱신 완료");

        } catch (Exception e) {
            log.error("[GOOGLE TOKEN][REFRESH] 오류: {}", e.getMessage(), e);
        }
    }
}
