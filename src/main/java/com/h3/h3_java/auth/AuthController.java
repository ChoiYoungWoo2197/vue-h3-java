package com.h3.h3_java.auth;

import com.h3.h3_java.raw.mongo.LogMongoService;
import com.h3.h3_java.raw.mongo.UserMongoService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/v1/h3/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserMongoService userMongo;
    private final LogMongoService  logMongo;
    private final JwtUtil          jwtUtil;

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequestDto req,
                                                     HttpServletRequest httpReq) {
        String ip = extractIp(httpReq);
        Map<String, Object> res = new LinkedHashMap<>();

        if (req.getUserid() == null || req.getUserpass() == null) {
            res.put("result", "failed");
            res.put("status", "1009");
            res.put("errormessage", "아이디 또는 비밀번호를 입력해주세요.");
            return ResponseEntity.badRequest().body(res);
        }

        Document user = userMongo.findByUserId(req.getUserid());

        if (user == null || user.getString("user_pass") == null) {
            logMongo.insertLoginLog(req.getUserid(), "n", ip, "");
            res.put("result", "failed");
            res.put("status", "1005");
            res.put("errormessage", "아이디 또는 비밀번호를 확인하고 입력해주세요.");
            return ResponseEntity.status(401).body(res);
        }

        String hashedPass = sha256(req.getUserpass());
        if (!hashedPass.equals(user.getString("user_pass"))) {
            logMongo.insertLoginLog(req.getUserid(), "n", ip, "");
            res.put("result", "failed");
            res.put("status", "1005");
            res.put("errormessage", "아이디 또는 비밀번호를 확인하고 입력해주세요.");
            return ResponseEntity.status(401).body(res);
        }

        String  userId    = user.getString("user_id");
        Integer userLevel = user.getInteger("user_level", 1);
        String  token     = jwtUtil.generateToken(userId, userLevel);

        // PHP login.php 와 동일: 로그인 성공 시 h3_login 기록
        logMongo.insertLoginLog(userId, "y", ip, token);

        Map<String, Object> userInfo = new LinkedHashMap<>();
        userInfo.put("userid",      userId);
        userInfo.put("username",    user.getString("user_name"));
        userInfo.put("useremail",   user.getString("user_email"));
        userInfo.put("usercompany", user.getString("user_company"));
        userInfo.put("userphone",   user.getString("user_phone"));
        userInfo.put("userlevel",   userLevel);
        userInfo.put("userstatus",  parseIntSafe(user.get("user_status"), 0));

        res.put("result",      "success");
        res.put("status",      "200");
        res.put("accessToken", token);
        res.put("userinfo",    userInfo);
        return ResponseEntity.ok(res);
    }

    private String extractIp(HttpServletRequest req) {
        String forwarded = req.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",")[0].trim();
        return req.getRemoteAddr();
    }

    private int parseIntSafe(Object val, int def) {
        if (val == null) return def;
        if (val instanceof Number) return ((Number) val).intValue();
        try { return Integer.parseInt(val.toString()); } catch (Exception e) { return def; }
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
