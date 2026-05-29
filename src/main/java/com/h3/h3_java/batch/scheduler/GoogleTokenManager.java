package com.h3.h3_java.batch.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.h3.h3_java.media.google.mapper.GoogleMapper;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Slf4j
@Getter
@Component
@RequiredArgsConstructor
public class GoogleTokenManager {

    private final GoogleMapper mapper;

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

    private static final ObjectMapper OM = new ObjectMapper();

    public String getAccessToken() {
        String token = mapper.selectGoogleAccessToken();
        if (token == null || token.isBlank()) {
            log.warn("[GOOGLE][TOKEN] access_token 없음");
            return null;
        }
        if (!verify(token)) {
            log.info("[GOOGLE][TOKEN] 만료됨, 갱신 시도");
            token = refresh();
        }
        return token;
    }

    private boolean verify(String token) {
        try {
            SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
            f.setConnectTimeout(10_000);
            f.setReadTimeout(10_000);
            RestTemplate rt = new RestTemplate(f);
            ResponseEntity<String> res = rt.getForEntity(
                "https://oauth2.googleapis.com/tokeninfo?access_token=" + token, String.class);
            return res.getStatusCode().value() == 200;
        } catch (Exception e) {
            log.warn("[GOOGLE][TOKEN][VERIFY] 실패: {}", e.getMessage());
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private String refresh() {
        try {
            String refreshToken = mapper.selectGoogleRefreshToken();
            if (refreshToken == null || refreshToken.isBlank()) {
                log.warn("[GOOGLE][TOKEN][REFRESH] refresh_token 없음");
                return null;
            }

            String formBody = "grant_type=refresh_token"
                + "&client_id=" + clientId
                + "&client_secret=" + clientSecret
                + "&redirect_uri=" + redirectUri
                + "&refresh_token=" + refreshToken;

            SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
            f.setConnectTimeout(15_000);
            f.setReadTimeout(15_000);
            RestTemplate rt = new RestTemplate(f);

            HttpHeaders h = new HttpHeaders();
            h.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            HttpEntity<String> entity = new HttpEntity<>(formBody, h);

            ResponseEntity<String> res = rt.exchange(
                "https://oauth2.googleapis.com/token", HttpMethod.POST, entity, String.class);

            if (res.getStatusCode().value() != 200) {
                log.warn("[GOOGLE][TOKEN][REFRESH] HTTP {}: {}", res.getStatusCode(), res.getBody());
                return null;
            }

            Map<String, Object> parsed = OM.readValue(res.getBody(), Map.class);
            String newToken = (String) parsed.get("access_token");
            if (newToken != null) {
                mapper.updateGoogleAccessToken(newToken);
                log.info("[GOOGLE][TOKEN][REFRESH] 갱신 완료");
            }
            return newToken;

        } catch (Exception e) {
            log.error("[GOOGLE][TOKEN][REFRESH] 오류: {}", e.getMessage(), e);
            return null;
        }
    }
}
