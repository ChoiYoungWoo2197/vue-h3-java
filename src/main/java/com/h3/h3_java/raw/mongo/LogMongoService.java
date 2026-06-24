package com.h3.h3_java.raw.mongo;

import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class LogMongoService {

    private final MongoTemplate mongo;
    private static final String LOGIN_COL = "h3_login";
    private static final String API_COL   = "h3_api_log";
    private static final String SKIP_IP   = "1.251.4.168";

    // PHP login.php 와 동일: INSERT INTO h3_login
    public void insertLoginLog(String userId, String status, String ip, String token) {
        if (SKIP_IP.equals(ip)) return;
        Document doc = new Document()
            .append("user_id",       userId != null ? userId : "")
            .append("login_status",  status)
            .append("login_ip",      ip != null ? ip : "")
            .append("login_token",   token != null ? token : "")
            .append("login_regdate", now());
        try { mongo.insert(doc, LOGIN_COL); } catch (Exception ignored) {}
    }

    // PHP common.php api_log() 와 동일: INSERT INTO h3_api_log
    public void insertApiLog(String userId, String url, String method, String status,
                             String request, String response, String referer, String ip) {
        if (SKIP_IP.equals(ip)) return;
        // 응답이 너무 크면 2000자로 제한 (PHP와 달리 MongoDB 문서 크기 고려)
        String respTrimmed = response != null && response.length() > 2000
            ? response.substring(0, 2000) + "...[truncated]"
            : (response != null ? response : "");
        Document doc = new Document()
            .append("user_id",      userId != null ? userId : "")
            .append("api_url",      url != null ? url : "")
            .append("api_method",   method != null ? method : "")
            .append("api_status",   status != null ? status : "")
            .append("api_request",  request != null ? request : "")
            .append("api_response", respTrimmed)
            .append("api_referer",  referer != null ? referer : "")
            .append("api_ip",       ip != null ? ip : "")
            .append("api_regdate",  now());
        try { mongo.insert(doc, API_COL); } catch (Exception ignored) {}
    }

    private String now() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}
