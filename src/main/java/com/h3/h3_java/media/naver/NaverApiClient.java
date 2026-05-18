package com.h3.h3_java.media.naver;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.util.Base64;
import java.util.Map;

@Slf4j
public class NaverApiClient {

    private static final String BASE_URL = "https://api.searchad.naver.com";

    private final String apiKey;
    private final String secretKey;
    private final String customerId;
    private final RestTemplate restTemplate = new RestTemplate();

    public NaverApiClient(String apiKey, String secretKey, String customerId) {
        this.apiKey = apiKey;
        this.secretKey = secretKey;
        this.customerId = customerId;
    }

    private String sign(long timestamp, String method, String path) {
        try {
            String data = timestamp + "." + method + "." + path;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretKey.getBytes(), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(data.getBytes()));
        } catch (Exception e) {
            throw new RuntimeException("HMAC sign failed", e);
        }
    }

    private HttpHeaders headers(String method, String path) {
        long ts = System.currentTimeMillis();
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.set("X-Timestamp", String.valueOf(ts));
        h.set("X-API-KEY", apiKey);
        h.set("X-Customer", customerId);
        h.set("X-Signature", sign(ts, method, path));
        return h;
    }

    public Map<String, Object> get(String path) {
        try {
            ResponseEntity<Map> res = restTemplate.exchange(
                BASE_URL + path, HttpMethod.GET, new HttpEntity<>(headers("GET", path)), Map.class
            );
            return res.getBody();
        } catch (Exception e) {
            log.error("[NaverApi] GET {} failed: {}", path, e.getMessage());
            return null;
        }
    }

    // 서명은 basePath만으로, 쿼리파라미터 인코딩은 UriComponentsBuilder에 위임
    public Map<String, Object> get(String basePath, Map<String, String> params) {
        try {
            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(BASE_URL + basePath);
            if (params != null) {
                params.forEach(builder::queryParam);
            }
            URI uri = builder.build().encode().toUri();
            ResponseEntity<Map> res = restTemplate.exchange(
                uri, HttpMethod.GET,
                new HttpEntity<>(headers("GET", basePath)),
                Map.class
            );
            return res.getBody();
        } catch (Exception e) {
            log.error("[NaverApi] GET {} failed: {}", basePath, e.getMessage());
            return null;
        }
    }

    public Map<String, Object> post(String path, Map<String, Object> body) {
        try {
            ResponseEntity<Map> res = restTemplate.exchange(
                BASE_URL + path, HttpMethod.POST, new HttpEntity<>(body, headers("POST", path)), Map.class
            );
            return res.getBody();
        } catch (Exception e) {
            log.error("[NaverApi] POST {} failed: {}", path, e.getMessage());
            return null;
        }
    }

    public void delete(String path) {
        try {
            restTemplate.exchange(
                BASE_URL + path, HttpMethod.DELETE, new HttpEntity<>(headers("DELETE", path)), Void.class
            );
        } catch (Exception e) {
            log.warn("[NaverApi] DELETE {} failed: {}", path, e.getMessage());
        }
    }

    public byte[] download(String url) {
        try {
            ResponseEntity<byte[]> res = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers("GET", "/report-download")), byte[].class
            );
            return res.getBody();
        } catch (Exception e) {
            log.error("[NaverApi] DOWNLOAD {} failed: {}", url, e.getMessage());
            return null;
        }
    }
}