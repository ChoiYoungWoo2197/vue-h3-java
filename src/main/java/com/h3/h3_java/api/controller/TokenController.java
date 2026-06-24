package com.h3.h3_java.api.controller;

import com.h3.h3_java.batch.scheduler.GoogleTokenManager;
import com.h3.h3_java.batch.scheduler.KakaoMoTokenManager;
import com.h3.h3_java.batch.scheduler.KakaoSaTokenManager;
import com.h3.h3_java.batch.scheduler.NaverGfaTokenManager;
import com.h3.h3_java.raw.mongo.AccountMongoService;
import com.h3.h3_java.raw.mongo.GoogleTokenMongoService;
import com.h3.h3_java.raw.mongo.KakaoMoTokenMongoService;
import com.h3.h3_java.raw.mongo.KakaoSaTokenMongoService;
import com.h3.h3_java.raw.mongo.NaverGfaTokenMongoService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * PHP naverlogin/callback.php, kakaoauth/*, googleauth/* 이식.
 * 4개 플랫폼 OAuth 인가·콜백·수동갱신 엔드포인트.
 * SecurityConfig: /v1/h3/token/** permitAll (콜백은 브라우저 리다이렉트)
 */
@Slf4j
@RestController
@RequestMapping("/v1/h3/token")
@RequiredArgsConstructor
public class TokenController {

    private final AccountMongoService       accountMongo;
    private final NaverGfaTokenMongoService naverGfaTokenMongo;
    private final KakaoSaTokenMongoService  kakaoSaTokenMongo;
    private final KakaoMoTokenMongoService  kakaoMoTokenMongo;
    private final GoogleTokenMongoService   googleTokenMongo;

    private final NaverGfaTokenManager naverGfaTokenManager;
    private final KakaoSaTokenManager  kakaoSaTokenManager;
    private final KakaoMoTokenManager  kakaoMoTokenManager;
    private final GoogleTokenManager   googleTokenManager;

    // ── Kakao SA ──────────────────────────────────────────────────────────────────

    @Value("${kakao.sa.client-id}")       private String kakaoSaClientId;
    @Value("${kakao.sa.client-secret}")   private String kakaoSaClientSecret;
    @Value("${kakao.sa.redirect-uri}")    private String kakaoSaRedirectUri;

    // ── Kakao MO ──────────────────────────────────────────────────────────────────

    @Value("${kakao.mo.client-id}")       private String kakaoMoClientId;
    @Value("${kakao.mo.client-secret}")   private String kakaoMoClientSecret;
    @Value("${kakao.mo.redirect-uri}")    private String kakaoMoRedirectUri;

    // ── Google ────────────────────────────────────────────────────────────────────

    @Value("${google.client-id}")         private String googleClientId;
    @Value("${google.client-secret}")     private String googleClientSecret;
    @Value("${google.redirect-uri}")      private String googleRedirectUri;

    // ── Naver GFA redirect URI (h3_account에서 읽거나 application.yml) ───────────

    @Value("${naver.gfa.redirect-uri}")   private String naverGfaRedirectUri;

    // =============================================================================
    // 네이버 GFA
    // =============================================================================

    /** PHP naverlogin/* 에 해당하는 인가 URL redirect */
    @GetMapping("/naver-gfa/oauth")
    public void naverGfaOauth(HttpServletResponse response) throws IOException {
        Document admin = accountMongo.findByUserId("admin");
        if (admin == null) { response.sendError(500, "admin 계정 없음"); return; }

        String clientId = admin.getString("account_gfa");
        String state    = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String url = "https://nid.naver.com/oauth2.0/authorize"
            + "?response_type=code"
            + "&client_id="    + clientId
            + "&redirect_uri=" + URLEncoder.encode(naverGfaRedirectUri, StandardCharsets.UTF_8)
            + "&state="        + state;
        response.sendRedirect(url);
    }

    /** PHP naverlogin/callback.php 이식: 인가코드 → 토큰 발급 + MongoDB 저장 */
    @GetMapping("/naver-gfa/callback")
    public ResponseEntity<Map<String, Object>> naverGfaCallback(
            @RequestParam String code,
            @RequestParam(required = false, defaultValue = "") String state) {

        Document admin = accountMongo.findByUserId("admin");
        if (admin == null) return fail("admin 계정 없음");

        String clientId     = admin.getString("account_gfa");
        String clientSecret = admin.getString("account_naver_secret");

        String url = "https://nid.naver.com/oauth2.0/token"
            + "?grant_type=authorization_code"
            + "&client_id="     + clientId
            + "&client_secret=" + clientSecret
            + "&redirect_uri="  + URLEncoder.encode(naverGfaRedirectUri, StandardCharsets.UTF_8)
            + "&code="          + code
            + "&state="         + state;

        try {
            RestTemplate rt  = new RestTemplate();
            Map<?, ?> body   = rt.getForObject(url, Map.class);
            if (body == null || body.get("access_token") == null)
                return fail("Naver GFA 토큰 발급 실패: " + body);

            String at = String.valueOf(body.get("access_token"));
            String rt2 = body.containsKey("refresh_token")
                ? String.valueOf(body.get("refresh_token")) : "";

            naverGfaTokenMongo.saveToken(at, rt2);
            log.info("[GFA TOKEN] 최초 발급 완료");
            return ok("네이버 GFA 토큰 발급 완료");
        } catch (Exception e) {
            log.error("[GFA TOKEN] callback 오류: {}", e.getMessage(), e);
            return fail(e.getMessage());
        }
    }

    /** 수동 갱신 트리거 */
    @PostMapping("/naver-gfa/refresh")
    public ResponseEntity<Map<String, Object>> naverGfaRefresh() {
        naverGfaTokenManager.forceRefresh();
        return ok("네이버 GFA 토큰 갱신 완료");
    }

    // =============================================================================
    // 카카오 SA
    // =============================================================================

    /** PHP kakaosa_oauth.php 이식: 인가 URL redirect */
    @GetMapping("/kakao-sa/oauth")
    public void kakaoSaOauth(HttpServletResponse response) throws IOException {
        String url = "https://kauth.kakao.com/oauth/authorize"
            + "?response_type=code"
            + "&client_id=" + kakaoSaClientId
            + "&redirect_uri=" + URLEncoder.encode(kakaoSaRedirectUri, StandardCharsets.UTF_8)
            + "&prompt=none";
        response.sendRedirect(url);
    }

    /** PHP kakaosa_access_token.php 이식: 인가코드 → 토큰 발급 + MongoDB 저장 */
    @GetMapping("/kakao-sa/callback")
    public ResponseEntity<Map<String, Object>> kakaoSaCallback(@RequestParam String code) {
        try {
            Map<?, ?> body = kakaoTokenRequest(code, kakaoSaClientId, kakaoSaClientSecret, kakaoSaRedirectUri);
            if (body == null || body.get("access_token") == null)
                return fail("카카오 SA 토큰 발급 실패");

            // PHP INSERT 필드 완전 동일: code, accesstoken, expires_in, refreshtoken, refresh_expires_in, type, tokentype
            kakaoSaTokenMongo.saveFullToken(
                code,
                String.valueOf(body.get("access_token")),
                body.get("expires_in"),
                body.containsKey("refresh_token") ? String.valueOf(body.get("refresh_token")) : "",
                body.get("refresh_token_expires_in"),
                body.containsKey("token_type") ? String.valueOf(body.get("token_type")) : ""
            );
            log.info("[KAKAO-SA TOKEN] 최초 발급 완료");
            return ok("카카오 SA 토큰 발급 완료");
        } catch (Exception e) {
            log.error("[KAKAO-SA TOKEN] callback 오류: {}", e.getMessage(), e);
            return fail(e.getMessage());
        }
    }

    /** 수동 갱신 */
    @PostMapping("/kakao-sa/refresh")
    public ResponseEntity<Map<String, Object>> kakaoSaRefresh() {
        kakaoSaTokenManager.forceRefresh();
        return ok("카카오 SA 토큰 갱신 완료");
    }

    // =============================================================================
    // 카카오 MO
    // =============================================================================

    /** PHP kakaomo_oauth.php 이식 — MO는 prompt=none 없음 (SA와 다름) */
    @GetMapping("/kakao-mo/oauth")
    public void kakaoMoOauth(HttpServletResponse response) throws IOException {
        String url = "https://kauth.kakao.com/oauth/authorize"
            + "?response_type=code"
            + "&client_id=" + kakaoMoClientId
            + "&redirect_uri=" + URLEncoder.encode(kakaoMoRedirectUri, StandardCharsets.UTF_8);
        response.sendRedirect(url);
    }

    /** PHP kakaomo_access_token.php 이식 */
    @GetMapping("/kakao-mo/callback")
    public ResponseEntity<Map<String, Object>> kakaoMoCallback(@RequestParam String code) {
        try {
            Map<?, ?> body = kakaoTokenRequest(code, kakaoMoClientId, kakaoMoClientSecret, kakaoMoRedirectUri);
            if (body == null || body.get("access_token") == null)
                return fail("카카오 MO 토큰 발급 실패");

            // PHP INSERT 필드 완전 동일: code, accesstoken, expires_in, refreshtoken, refresh_expires_in, type, tokentype
            kakaoMoTokenMongo.saveFullToken(
                code,
                String.valueOf(body.get("access_token")),
                body.get("expires_in"),
                body.containsKey("refresh_token") ? String.valueOf(body.get("refresh_token")) : "",
                body.get("refresh_token_expires_in"),
                body.containsKey("token_type") ? String.valueOf(body.get("token_type")) : ""
            );
            log.info("[KAKAO-MO TOKEN] 최초 발급 완료");
            return ok("카카오 MO 토큰 발급 완료");
        } catch (Exception e) {
            log.error("[KAKAO-MO TOKEN] callback 오류: {}", e.getMessage(), e);
            return fail(e.getMessage());
        }
    }

    /** 수동 갱신 */
    @PostMapping("/kakao-mo/refresh")
    public ResponseEntity<Map<String, Object>> kakaoMoRefresh() {
        kakaoMoTokenManager.forceRefresh();
        return ok("카카오 MO 토큰 갱신 완료");
    }

    // =============================================================================
    // 구글
    // =============================================================================

    /** PHP googlesa_oauth.php 이식 */
    @GetMapping("/google/oauth")
    public void googleOauth(HttpServletResponse response) throws IOException {
        String url = "https://accounts.google.com/o/oauth2/v2/auth"
            + "?response_type=code"
            + "&client_id="    + URLEncoder.encode(googleClientId, StandardCharsets.UTF_8)
            + "&redirect_uri=" + googleRedirectUri
            + "&scope="        + URLEncoder.encode("https://www.googleapis.com/auth/adwords", StandardCharsets.UTF_8)
            + "&include_granted_scopes=true"
            + "&access_type=offline"
            + "&prompt=consent";
        response.sendRedirect(url);
    }

    /** PHP googlesa_access_token.php 이식: 인가코드 → 토큰 발급 + MongoDB 저장 */
    @GetMapping("/google/callback")
    public ResponseEntity<Map<String, Object>> googleCallback(@RequestParam String code) {
        try {
            RestTemplate rt = new RestTemplate();
            HttpHeaders h = new HttpHeaders();
            h.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("grant_type",    "authorization_code");
            params.add("client_id",     googleClientId);
            params.add("client_secret", googleClientSecret);
            params.add("redirect_uri",  googleRedirectUri);
            params.add("code",          code);

            Map<?, ?> body = rt.postForObject(
                "https://oauth2.googleapis.com/token",
                new HttpEntity<>(params, h), Map.class);

            if (body == null || body.get("access_token") == null)
                return fail("구글 토큰 발급 실패");

            googleTokenMongo.saveFullToken(
                code,
                String.valueOf(body.get("access_token")),
                body.containsKey("refresh_token") ? String.valueOf(body.get("refresh_token")) : "",
                body.get("expires_in"),
                body.containsKey("id_token")   ? String.valueOf(body.get("id_token"))   : "",
                body.containsKey("scope")      ? String.valueOf(body.get("scope"))      : "",
                body.containsKey("token_type") ? String.valueOf(body.get("token_type")) : ""
            );
            log.info("[GOOGLE][TOKEN] 최초 발급 완료");
            return ok("구글 토큰 발급 완료");
        } catch (Exception e) {
            log.error("[GOOGLE][TOKEN] callback 오류: {}", e.getMessage(), e);
            return fail(e.getMessage());
        }
    }

    /** 수동 갱신 */
    @PostMapping("/google/refresh")
    public ResponseEntity<Map<String, Object>> googleRefresh() {
        googleTokenManager.forceRefresh();
        return ok("구글 토큰 갱신 완료");
    }

    // =============================================================================
    // 공통
    // =============================================================================

    private Map<?, ?> kakaoTokenRequest(String code, String clientId, String clientSecret, String redirectUri) {
        RestTemplate rt = new RestTemplate();
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type",    "authorization_code");
        params.add("client_id",     clientId);
        params.add("client_secret", clientSecret);
        params.add("redirect_uri",  redirectUri);
        params.add("code",          code);

        return rt.postForObject("https://kauth.kakao.com/oauth/token",
            new HttpEntity<>(params, h), Map.class);
    }

    private ResponseEntity<Map<String, Object>> ok(String message) {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("result",  "success");
        res.put("status",  "200");
        res.put("message", message);
        return ResponseEntity.ok(res);
    }

    private ResponseEntity<Map<String, Object>> fail(String message) {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("result",       "failed");
        res.put("status",       "500");
        res.put("errormessage", message);
        return ResponseEntity.status(500).body(res);
    }
}
