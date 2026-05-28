package com.h3.h3_java.media.kakao;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Map;

@Slf4j
public class KakaoSaApiClient {

    private static final String BASE_URL  = "https://api.keywordad.kakao.com";
    private static final int    MAX_RETRY = 5;

    private final String accessToken;
    private final String adAccountId;
    private final RestTemplate restTemplate;

    public KakaoSaApiClient(String accessToken, String adAccountId) {
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

    /** 캠페인/광고그룹/키워드 목록 등 배열 응답 */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getList(String path, Map<String, String> params) {
        URI uri = buildUri(path, params);

        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            try {
                ResponseEntity<List> res = restTemplate.exchange(
                    uri, HttpMethod.GET,
                    new HttpEntity<>(headers()), List.class
                );
                return res.getBody();
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode().value() == 429) {
                    long wait = attempt * 5000L;
                    log.warn("[KAKAO-SA] 429 GET {} attempt={} wait={}ms", path, attempt, wait);
                    sleep(wait);
                } else if (e.getStatusCode().value() == 401) {
                    log.error("[KAKAO-SA] 401 Unauthorized GET {} - 토큰 만료", path);
                    return null;
                } else {
                    log.error("[KAKAO-SA] GET {} failed: {} {}", path, e.getStatusCode(), e.getMessage());
                    return null;
                }
            } catch (Exception e) {
                log.error("[KAKAO-SA] GET {} failed: {}", path, e.getMessage());
                return null;
            }
        }
        log.error("[KAKAO-SA] GET {} failed after {} retries", path, MAX_RETRY);
        return null;
    }

    /** 보고서/단건 상세 등 객체 응답 */
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
                    log.warn("[KAKAO-SA] 429 GET {} attempt={} wait={}ms", path, attempt, wait);
                    sleep(wait);
                } else if (e.getStatusCode().value() == 401) {
                    log.error("[KAKAO-SA] 401 Unauthorized GET {} - 토큰 만료", path);
                    return null;
                } else {
                    log.error("[KAKAO-SA] GET {} failed: {} {}", path, e.getStatusCode(), e.getMessage());
                    return null;
                }
            } catch (Exception e) {
                log.error("[KAKAO-SA] GET {} failed: {}", path, e.getMessage());
                return null;
            }
        }
        log.error("[KAKAO-SA] GET {} failed after {} retries", path, MAX_RETRY);
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
