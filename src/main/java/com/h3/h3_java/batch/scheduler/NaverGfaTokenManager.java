package com.h3.h3_java.batch.scheduler;

import com.h3.h3_java.media.naver.dto.NaverGfaAdminDto;
import com.h3.h3_java.media.naver.mapper.NaverGfaMapper;
import com.h3.h3_java.raw.mongo.NaverGfaTokenMongoService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class NaverGfaTokenManager {

    private final NaverGfaMapper            mapper;
    private final NaverGfaTokenMongoService mongoService;

    private static final String VERIFY_URL  = "https://openapi.naver.com/v1/nid/verify";
    private static final String REFRESH_URL = "https://nid.naver.com/oauth2.0/token";

    private volatile String       accessToken;
    private          NaverGfaAdminDto adminAccount;

    // ── 초기화 ────────────────────────────────────────────────────────────────

    @PostConstruct
    public void init() {
        adminAccount = mapper.selectGfaAdminAccount();
        if (adminAccount == null || adminAccount.getAccountGfa() == null) {
            log.warn("[GFA TOKEN] admin 계정 없음 - h3_account 확인 필요");
            return;
        }

        Document token = mongoService.findToken();

        if (token == null) {
            log.info("[GFA TOKEN] MongoDB 토큰 없음 → MySQL 마이그레이션 시도");
            migrateFromMySQL();
        } else {
            accessToken = token.getString("access_token");
            log.info("[GFA TOKEN] MongoDB 토큰 로드 완료");
        }
    }

    // ── 30분 주기 자동 갱신 ────────────────────────────────────────────────────

    @Scheduled(fixedDelay = 1_800_000, initialDelay = 60_000)
    public void autoRefresh() {
        if (accessToken == null || adminAccount == null) return;

        if (!isTokenValid()) {
            log.info("[GFA TOKEN] 만료 감지 → 갱신 시작");
            doRefresh();
        }
    }

    // ── 외부 접근 메서드 ───────────────────────────────────────────────────────

    public String getAccessToken() {
        return accessToken;
    }

    public String getAccessManagerAccountNo() {
        return adminAccount != null ? adminAccount.getAccountNaverCustomer() : null;
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private void migrateFromMySQL() {
        try {
            Map<String, Object> row = mapper.selectLatestNaverToken();
            if (row == null || row.get("access_token") == null) {
                log.warn("[GFA TOKEN] MySQL에도 토큰 없음 - 수동 등록 필요");
                return;
            }
            String at = String.valueOf(row.get("access_token"));
            String rt = String.valueOf(row.get("refresh_token"));
            mongoService.saveToken(at, rt);
            accessToken = at;
            log.info("[GFA TOKEN] MySQL → MongoDB 마이그레이션 완료");
        } catch (Exception e) {
            log.error("[GFA TOKEN] 마이그레이션 실패: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private boolean isTokenValid() {
        try {
            RestTemplate rt = new RestTemplate();
            var headers = new org.springframework.http.HttpHeaders();
            headers.set("Authorization", "Bearer " + accessToken);
            ResponseEntity<Map> res = rt.exchange(
                VERIFY_URL,
                org.springframework.http.HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(headers),
                Map.class
            );
            Map<String, Object> body = res.getBody();
            return body == null || !"24".equals(String.valueOf(body.get("resultcode")));
        } catch (Exception e) {
            log.warn("[GFA TOKEN] verify 실패: {}", e.getMessage());
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private void doRefresh() {
        try {
            Document current = mongoService.findToken();
            if (current == null) return;

            String refreshToken = current.getString("refresh_token");
            String url = REFRESH_URL
                + "?grant_type=refresh_token"
                + "&client_id="     + adminAccount.getAccountGfa()
                + "&client_secret=" + adminAccount.getAccountNaverSecret()
                + "&refresh_token=" + refreshToken;

            RestTemplate rt = new RestTemplate();
            ResponseEntity<Map> res = rt.getForEntity(url, Map.class);
            Map<String, Object> body = res.getBody();

            if (body == null || body.get("access_token") == null) {
                log.error("[GFA TOKEN] 갱신 응답 이상: {}", body);
                return;
            }

            String newAt = String.valueOf(body.get("access_token"));
            String newRt = body.containsKey("refresh_token")
                ? String.valueOf(body.get("refresh_token"))
                : refreshToken;

            mongoService.saveToken(newAt, newRt);
            accessToken = newAt;
            log.info("[GFA TOKEN] 갱신 완료");

        } catch (Exception e) {
            log.error("[GFA TOKEN] 갱신 실패: {}", e.getMessage());
        }
    }
}
