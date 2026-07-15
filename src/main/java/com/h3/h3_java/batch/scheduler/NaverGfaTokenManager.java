package com.h3.h3_java.batch.scheduler;

import com.h3.h3_java.media.naver.dto.NaverGfaAdminDto;
import com.h3.h3_java.raw.mongo.AccountMongoService;
import com.h3.h3_java.raw.mongo.NaverGfaTokenMongoService;
import com.h3.h3_java.util.CryptoUtil;
import org.bson.Document;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class NaverGfaTokenManager {

    private final AccountMongoService    accountMongo;
    private final NaverGfaTokenMongoService tokenMongoService;

    private static final String VERIFY_URL  = "https://openapi.naver.com/v1/nid/verify";
    private static final String REFRESH_URL = "https://nid.naver.com/oauth2.0/token";

    private volatile String   accessToken;
    private NaverGfaAdminDto  adminAccount;

    @PostConstruct
    public void init() {
        Document acct = accountMongo.findByUserId("admin");
        if (acct == null || acct.getString("account_gfa") == null) {
            log.warn("[GFA TOKEN] admin 계정 없음 - h3_account 확인 필요");
            return;
        }
        adminAccount = new NaverGfaAdminDto();
        adminAccount.setAccountGfa(acct.getString("account_gfa"));
        adminAccount.setAccountNaverSecret(CryptoUtil.safeDecrypt(acct.getString("account_naver_secret")));
        adminAccount.setAccountNaverCustomer(acct.getString("account_naver_customer"));

        Document tok = tokenMongoService.findToken();
        if (tok == null || tok.getString("access_token") == null) {
            log.warn("[GFA TOKEN] MongoDB 토큰 없음 - 수동 등록 필요");
            return;
        }
        accessToken = tok.getString("access_token");
        log.info("[GFA TOKEN] MongoDB 토큰 로드 완료");
    }

    @Scheduled(fixedDelay = 1_800_000, initialDelay = 60_000)
    public void autoRefresh() {
        if (accessToken == null || adminAccount == null) return;

        if (!isTokenValid()) {
            log.info("[GFA TOKEN] 만료 감지 → 갱신 시작");
            doRefresh();
        }
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getAccessManagerAccountNo() {
        return adminAccount != null ? adminAccount.getAccountNaverCustomer() : null;
    }

    public void forceRefresh() {
        doRefresh();
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
            Document tok = tokenMongoService.findToken();
            if (tok == null) return;

            String refreshToken = tok.getString("refresh_token");
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

            tokenMongoService.saveToken(newAt, newRt);
            accessToken = newAt;
            log.info("[GFA TOKEN] 갱신 완료");

        } catch (Exception e) {
            log.error("[GFA TOKEN] 갱신 실패: {}", e.getMessage());
        }
    }
}
