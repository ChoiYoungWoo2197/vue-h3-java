package com.h3.h3_java.media.google;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Google Ads searchStream API 클라이언트.
 * POST /v20/customers/{customerId}/googleAds:searchStream
 * Body: {"query": "GAQL..."}
 * Response: [{results:[...]}, ...] — results[0] 사용
 */
@Slf4j
public class GoogleApiClient {

    private static final String BASE_URL    = "https://googleads.googleapis.com/v21";
    private static final int    MAX_RETRY   = 3;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String      token;
    private final String      developerToken;
    private final String      managerId;
    private final RestTemplate restTemplate;

    public GoogleApiClient(String token, String developerToken, String managerId) {
        this.token         = token;
        this.developerToken = developerToken;
        this.managerId     = managerId;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(15_000);
        factory.setReadTimeout(120_000);
        this.restTemplate = new RestTemplate(factory);
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> searchStream(String customerId, String query) {
        String url = BASE_URL + "/customers/" + customerId + "/googleAds:searchStream";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("developer-token", developerToken);
        headers.set("login-customer-id", managerId);
        headers.set("Authorization", "Bearer " + token);

        String body;
        try {
            body = MAPPER.writeValueAsString(Map.of("query", query));
        } catch (Exception e) {
            log.error("[GOOGLE] JSON 직렬화 오류: {}", e.getMessage());
            return Collections.emptyList();
        }

        HttpEntity<String> entity = new HttpEntity<>(body, headers);

        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            try {
                ResponseEntity<List> res = restTemplate.exchange(url, HttpMethod.POST, entity, List.class);
                List<?> outer = res.getBody();
                if (outer == null || outer.isEmpty()) return Collections.emptyList();

                Object firstChunk = outer.get(0);
                if (!(firstChunk instanceof Map)) return Collections.emptyList();

                Object results = ((Map<?, ?>) firstChunk).get("results");
                if (!(results instanceof List)) return Collections.emptyList();

                return (List<Map<String, Object>>) results;

            } catch (HttpClientErrorException e) {
                int status = e.getStatusCode().value();
                if (status == 429) {
                    long wait = attempt * 5_000L;
                    log.warn("[GOOGLE] 429 attempt={} wait={}ms query={}", attempt, wait, query.substring(0, Math.min(query.length(), 60)));
                    sleep(wait);
                } else {
                    log.error("[GOOGLE] HTTP {} url={} body={}", status, url, e.getResponseBodyAsString());
                    return Collections.emptyList();
                }
            } catch (Exception e) {
                log.error("[GOOGLE] searchStream 오류 url={} err={}", url, e.getMessage());
                return Collections.emptyList();
            }
        }
        return Collections.emptyList();
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
