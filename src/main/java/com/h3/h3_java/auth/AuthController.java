package com.h3.h3_java.auth;

import lombok.RequiredArgsConstructor;
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

    private final UserMapper userMapper;
    private final JwtUtil    jwtUtil;

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequestDto req) {
        Map<String, Object> res = new LinkedHashMap<>();

        if (req.getUserid() == null || req.getUserpass() == null) {
            res.put("result", "failed");
            res.put("status", "1009");
            res.put("errormessage", "아이디 또는 비밀번호를 입력해주세요.");
            return ResponseEntity.badRequest().body(res);
        }

        UserDto user = userMapper.selectByUserId(req.getUserid());

        if (user == null || user.getUserPass() == null) {
            res.put("result", "failed");
            res.put("status", "1005");
            res.put("errormessage", "아이디 또는 비밀번호를 확인하고 입력해주세요.");
            return ResponseEntity.status(401).body(res);
        }

        String hashedPass = sha256(req.getUserpass());
        if (!hashedPass.equals(user.getUserPass())) {
            res.put("result", "failed");
            res.put("status", "1005");
            res.put("errormessage", "아이디 또는 비밀번호를 확인하고 입력해주세요.");
            return ResponseEntity.status(401).body(res);
        }

        String token = jwtUtil.generateToken(user.getUserId(), user.getUserLevel());

        Map<String, Object> userInfo = new LinkedHashMap<>();
        userInfo.put("userid",      user.getUserId());
        userInfo.put("username",    user.getUserName());
        userInfo.put("useremail",   user.getUserEmail());
        userInfo.put("usercompany", user.getUserCompany());
        userInfo.put("userphone",   user.getUserPhone());
        userInfo.put("userlevel",   user.getUserLevel());
        userInfo.put("userstatus",  user.getUserStatus());

        res.put("result",      "success");
        res.put("status",      "200");
        res.put("accessToken", token);
        res.put("userinfo",    userInfo);
        return ResponseEntity.ok(res);
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
