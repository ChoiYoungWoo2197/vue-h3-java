package com.h3.h3_java.media.kakao;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Kakao Moment API 클라이언트.
 * Base URL: https://apis.moment.kakao.com
 * MO API v4는 목록 응답이 {"content": [...]} 페이지 구조.
 */
@Slf4j
public class KakaoMoApiClient {

    private static final String BASE_URL  = "https://apis.moment.kakao.com";
    private static final int    MAX_RETRY = 5;

    private final String accessToken;
    private final String adAccountId;
    private final RestTemplate restTemplate;

    public KakaoMoApiClient(String accessToken, String adAccountId) {
        this.accessToken  = accessToken;
        this.adAccountId  = adAccountId;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(120_000);
        this.restTemplate = new RestTemplate(factory);
    }

    private HttpHeaders headers() {
        HttpHeaders h = new HttpHeaders();
        h.set("Authorization", "Bearer " + accessToken);
        h.set("adAccountId", adAccountId);
        return h;
    }

    /** 목록 API (content 배열 추출) - campaigns, adGroups, creatives */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getContent(String path, Map<String, String> params) {
        Map<String, Object> res = get(path, params);
        if (res == null) return Collections.emptyList();
        Object content = res.get("content");
        if (!(content instanceof List)) return Collections.emptyList();
        return (List<Map<String, Object>>) content;
    }

    /** 보고서/단건 등 객체 응답 */
    @SuppressWarnings("unchecked")
    public Map<String, Object> get(String path, Map<String, String> params) {
        URI uri = buildUri(path, params);

        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            try {
                ResponseEntity<Map> res = restTemplate.exchange(
                    uri, HttpMethod.GET,
                    new HttpEntity<>(headers()), Map.class
                );
                return res.getBody();
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode().value() == 429) {
                    long wait = attempt * 5000L;
                    log.warn("[KAKAO-MO] 429 GET {} attempt={} wait={}ms", path, attempt, wait);
                    sleep(wait);
                } else if (e.getStatusCode().value() == 401) {
                    log.error("[KAKAO-MO] 401 Unauthorized GET {} - 토큰 만료", path);
                    return null;
                } else {
                    log.error("[KAKAO-MO] GET {} failed: {} {}", path, e.getStatusCode(), e.getMessage());
                    return null;
                }
            } catch (Exception e) {
                log.error("[KAKAO-MO] GET {} failed: {}", path, e.getMessage());
                return null;
            }
        }
        log.error("[KAKAO-MO] GET {} failed after {} retries", path, MAX_RETRY);
        return null;
    }

    public Map<String, Object> get(String path) {
        return get(path, null);
    }

    private URI buildUri(String path, Map<String, String> params) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(BASE_URL + path);
        if (params != null) params.forEach(builder::queryParam);
        return builder.build().encode().toUri();
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
