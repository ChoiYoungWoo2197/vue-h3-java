package com.h3.h3_java.media.naver;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
public class NaverApiClient {

    private static final String BASE_URL  = "https://api.searchad.naver.com";
    private static final int    MAX_RETRY = 5;

    // 계정(API KEY)당 동시 요청 수 제한 - 429 방지
    private final Semaphore rateLimiter = new Semaphore(3);

    // 같은 밀리초에 동일 서명 생성 방지 - Naver는 동일 서명 재사용을 invalid로 거부
    private final AtomicLong lastTs = new AtomicLong(0);

    private final String apiKey;
    private final String secretKey;
    private final String customerId;
    private final RestTemplate restTemplate;

    public NaverApiClient(String apiKey, String secretKey, String customerId) {
        this.apiKey     = apiKey;
        this.secretKey  = secretKey;
        this.customerId = customerId;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(180_000);
        this.restTemplate = new RestTemplate(factory);
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

    private long uniqueTs() {
        long now = System.currentTimeMillis();
        return lastTs.updateAndGet(prev -> Math.max(prev + 1, now));
    }

    private HttpHeaders headers(String method, String path) {
        long ts = uniqueTs();
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.set("X-Timestamp", String.valueOf(ts));
        h.set("X-API-KEY", apiKey);
        h.set("X-Customer", customerId);
        h.set("X-Signature", sign(ts, method, path));
        return h;
    }

    public Map<String, Object> get(String path) {
        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            try {
                rateLimiter.acquire();
                try {
                    ResponseEntity<Map> res = restTemplate.exchange(
                        BASE_URL + path, HttpMethod.GET,
                        new HttpEntity<>(headers("GET", path)), Map.class
                    );
                    return res.getBody();
                } finally {
                    rateLimiter.release();
                }
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode().value() == 429) {
                    long wait = attempt * 3000L;
                    log.warn("[NaverApi] 429 GET {} attempt={} wait={}ms", path, attempt, wait);
                    sleep(wait);
                } else {
                    log.error("[NaverApi] GET {} failed: {}", path, e.getMessage());
                    return null;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            } catch (Exception e) {
                log.error("[NaverApi] GET {} failed: {}", path, e.getMessage());
                return null;
            }
        }
        log.error("[NaverApi] GET {} failed after {} retries", path, MAX_RETRY);
        return null;
    }

    // 서명은 basePath만으로, 쿼리파라미터 인코딩은 UriComponentsBuilder에 위임
    public Map<String, Object> get(String basePath, Map<String, String> params) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(BASE_URL + basePath);
        if (params != null) params.forEach(builder::queryParam);
        URI uri = builder.build().encode().toUri();

        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            try {
                rateLimiter.acquire();
                try {
                    ResponseEntity<Map> res = restTemplate.exchange(
                        uri, HttpMethod.GET,
                        new HttpEntity<>(headers("GET", basePath)), Map.class
                    );
                    return res.getBody();
                } finally {
                    rateLimiter.release();
                }
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode().value() == 429) {
                    long wait = attempt * 3000L;
                    log.warn("[NaverApi] 429 GET {} attempt={} wait={}ms", basePath, attempt, wait);
                    sleep(wait);
                } else {
                    log.error("[NaverApi] GET {} failed: {}", basePath, e.getMessage());
                    return null;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            } catch (Exception e) {
                log.error("[NaverApi] GET {} failed: {}", basePath, e.getMessage());
                return null;
            }
        }
        log.error("[NaverApi] GET {} failed after {} retries", basePath, MAX_RETRY);
        return null;
    }

    public Map<String, Object> post(String path, Map<String, Object> body) {
        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            try {
                rateLimiter.acquire();
                try {
                    ResponseEntity<Map> res = restTemplate.exchange(
                        BASE_URL + path, HttpMethod.POST,
                        new HttpEntity<>(body, headers("POST", path)), Map.class
                    );
                    return res.getBody();
                } finally {
                    rateLimiter.release();
                }
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode().value() == 429) {
                    long wait = attempt * 3000L;
                    log.warn("[NaverApi] 429 POST {} attempt={} wait={}ms", path, attempt, wait);
                    sleep(wait);
                } else {
                    log.error("[NaverApi] POST {} failed: {}", path, e.getMessage());
                    return null;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            } catch (Exception e) {
                log.error("[NaverApi] POST {} failed: {}", path, e.getMessage());
                return null;
            }
        }
        log.error("[NaverApi] POST {} failed after {} retries", path, MAX_RETRY);
        return null;
    }

    public void delete(String path) {
        try {
            restTemplate.exchange(
                BASE_URL + path, HttpMethod.DELETE,
                new HttpEntity<>(headers("DELETE", path)), Void.class
            );
        } catch (Exception e) {
            log.warn("[NaverApi] DELETE {} failed: {}", path, e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getList(String basePath, Map<String, String> params) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(BASE_URL + basePath);
        if (params != null) params.forEach(builder::queryParam);
        URI uri = builder.build().encode().toUri();

        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            try {
                rateLimiter.acquire();
                try {
                    ResponseEntity<List> res = restTemplate.exchange(
                        uri, HttpMethod.GET,
                        new HttpEntity<>(headers("GET", basePath)), List.class
                    );
                    return res.getBody();
                } finally {
                    rateLimiter.release();
                }
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode().value() == 429) {
                    long wait = attempt * 3000L;
                    log.warn("[NaverApi] 429 GET {} attempt={} wait={}ms", basePath, attempt, wait);
                    sleep(wait);
                } else {
                    log.error("[NaverApi] GET {} failed: {}", basePath, e.getMessage());
                    return null;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            } catch (Exception e) {
                log.error("[NaverApi] GET {} failed: {}", basePath, e.getMessage());
                return null;
            }
        }
        log.error("[NaverApi] GET {} failed after {} retries", basePath, MAX_RETRY);
        return null;
    }

    public byte[] download(String url) {
        log.info("[NaverApi] DOWNLOAD start url={}", url);
        try {
            CompletableFuture<byte[]> future = CompletableFuture.supplyAsync(() -> {
                ResponseEntity<byte[]> res = restTemplate.exchange(
                    url, HttpMethod.GET,
                    new HttpEntity<>(headers("GET", "/report-download")), byte[].class
                );
                return res.getBody();
            });
            byte[] data = future.get(3, TimeUnit.MINUTES);
            log.info("[NaverApi] DOWNLOAD done bytes={}", data != null ? data.length : 0);
            return data;
        } catch (TimeoutException e) {
            log.error("[NaverApi] DOWNLOAD timeout(3min) url={}", url);
            return null;
        } catch (Exception e) {
            log.error("[NaverApi] DOWNLOAD {} failed: {}", url, e.getMessage());
            return null;
        }
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}