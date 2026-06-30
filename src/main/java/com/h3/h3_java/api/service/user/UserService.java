package com.h3.h3_java.api.service.user;

import com.h3.h3_java.raw.mongo.UserMongoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMongoService userMongo;

    // -------------------------------------------------------------------------
    // 회원정보 수정
    // -------------------------------------------------------------------------

    public Map<String, Object> updateUser(String userId, String pass, String name,
                                          String company, String email, String phone) {
        userMongo.updateInfo(userId, name, company, email, phone);

        if (pass != null && !pass.isBlank()) {
            String hashed     = sha256(pass);
            String passupdate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            userMongo.updatePassword(userId, hashed, passupdate);
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("result", "success");
        res.put("status", "200");
        return res;
    }

    // -------------------------------------------------------------------------
    // 이메일 중복 체크
    // -------------------------------------------------------------------------

    public Map<String, Object> checkEmail(String currentEmail, String newEmail) {
        if (newEmail == null || newEmail.isBlank()) {
            return fail("1006", "이메일을 입력해주세요.");
        }
        // 현재 이메일과 동일하면 패스
        if (newEmail.equals(currentEmail)) {
            return success();
        }
        if (userMongo.existsByEmail(newEmail)) {
            return fail("1006", "사용할 수 없는 이메일 입니다.");
        }
        return success();
    }


    // -------------------------------------------------------------------------

    private Map<String, Object> success() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("result", "success");
        r.put("status", "200");
        return r;
    }

    private Map<String, Object> fail(String status, String message) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("result",       "failed");
        r.put("status",       status);
        r.put("errormessage", message);
        return r;
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
