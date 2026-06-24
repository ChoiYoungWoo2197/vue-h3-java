package com.h3.h3_java.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.h3.h3_java.raw.mongo.LogMongoService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

/**
 * PHP common.php response_json() → api_log() 와 동일.
 * /v1/h3/** 모든 요청·응답을 MongoDB h3_api_log 에 기록.
 * Spring Security 필터 이후에 실행되어 SecurityContext 사용 가능.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
public class ApiLogFilter extends OncePerRequestFilter {

    private final LogMongoService logMongo;
    private final JwtUtil         jwtUtil;
    private final ObjectMapper    objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/v1/h3/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        ContentCachingRequestWrapper  cachedReq  = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper cachedResp = new ContentCachingResponseWrapper(response);

        try {
            chain.doFilter(cachedReq, cachedResp);
        } finally {
            try { saveLog(cachedReq, cachedResp); } catch (Exception ignored) {}
            cachedResp.copyBodyToResponse();
        }
    }

    private void saveLog(ContentCachingRequestWrapper req,
                         ContentCachingResponseWrapper resp) {
        String ip       = extractIp(req);
        String userId   = extractUserId(req);
        String url      = req.getRequestURI();
        String method   = req.getMethod();
        String reqBody  = buildReqBody(req);
        String respBody = new String(resp.getContentAsByteArray(), StandardCharsets.UTF_8);
        String status   = extractStatus(respBody);
        String referer  = req.getHeader("Referer");

        logMongo.insertApiLog(userId, url, method, status, reqBody, respBody, referer, ip);
    }

    private String extractIp(HttpServletRequest req) {
        String forwarded = req.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",")[0].trim();
        return req.getRemoteAddr();
    }

    private String extractUserId(HttpServletRequest req) {
        // 1순위: SecurityContext (JwtFilter가 설정한 인증 정보)
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()
                && auth.getPrincipal() instanceof String s && !s.equals("anonymousUser")) {
            return s;
        }
        // 2순위: Authorization 헤더 직접 파싱 (SecurityContext 미설정 경우 대비)
        String header = req.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try { return jwtUtil.extractUserId(header.substring(7)); } catch (Exception ignored) {}
        }
        // 3순위: request param (form POST)
        String param = req.getParameter("userid");
        if (param != null && !param.isBlank()) return param;
        // 4순위: JSON body userid 필드 (login 엔드포인트)
        try {
            byte[] bytes = req.getContentAsByteArray();
            if (bytes.length > 0) {
                JsonNode node = objectMapper.readTree(new String(bytes, StandardCharsets.UTF_8));
                JsonNode uid = node.get("userid");
                if (uid != null && !uid.isNull()) return uid.asText();
            }
        } catch (Exception ignored) {}
        return "";
    }

    private String buildReqBody(ContentCachingRequestWrapper req) {
        byte[] bytes = req.getContentAsByteArray();
        String body;
        if (bytes.length > 0) {
            body = new String(bytes, StandardCharsets.UTF_8);
        } else {
            // form params 방식 (ContentCaching이 body를 못 잡은 경우 fallback)
            body = req.getParameterMap().entrySet().stream()
                .map(e -> e.getKey() + "=" + String.join(",", e.getValue()))
                .collect(Collectors.joining("&"));
        }
        // PHP와 동일: userpass는 SHA256 해시값 또는 마스킹으로 저장
        return body.replaceAll("(?i)(\"userpass\"\\s*:\\s*\")[^\"]*\"", "$1***\"")
                   .replaceAll("(?i)(userpass=)[^&]*", "$1***");
    }

    private String extractStatus(String respBody) {
        try {
            JsonNode node = objectMapper.readTree(respBody);
            JsonNode s = node.get("status");
            return s != null ? s.asText() : "";
        } catch (Exception e) {
            return "";
        }
    }
}
