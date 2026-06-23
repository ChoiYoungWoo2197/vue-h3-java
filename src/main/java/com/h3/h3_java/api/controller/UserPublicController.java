package com.h3.h3_java.api.controller;

import com.h3.h3_java.raw.mongo.UserMongoService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 인증 불필요 회원 관련 API (회원가입·중복 체크 등).
 * Security: anyRequest().permitAll() 로 커버됨.
 */
@RestController
@RequestMapping("/v1/h3/user")
@RequiredArgsConstructor
public class UserPublicController {

    private final UserMongoService userMongo;

    /**
     * 아이디 또는 이메일 중복 체크 (PHP exists.php 이식).
     * - userid 만  → user_id 중복 여부
     * - useremail 만 → user_email 중복 + 형식 검증
     * - 둘 다 → 에러
     */
    @PostMapping("/exists")
    public Map<String, Object> exists(
            @RequestParam(required = false, defaultValue = "") String userid,
            @RequestParam(required = false, defaultValue = "") String useremail) {

        boolean hasId    = !userid.isBlank();
        boolean hasEmail = !useremail.isBlank();

        if (hasId && hasEmail) {
            return Map.of("result", "failed", "status", "00007",
                          "errormessage", "하나만 검색 가능합니다.");
        }

        if (hasId) {
            if (userMongo.existsByUserId(userid)) {
                return Map.of("result", "failed", "status", "1005",
                              "errormessage", "사용할 수 없는 아이디 입니다.");
            }
            return Map.of("result", "success", "status", "200");
        }

        if (hasEmail) {
            if (userMongo.existsByEmail(useremail)) {
                return Map.of("result", "failed", "status", "1006",
                              "errormessage", "사용할 수 없는 이메일 입니다.");
            }
            if (!useremail.matches("^[A-Za-z0-9+_.%-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
                return Map.of("result", "failed", "status", "1006",
                              "errormessage", "사용할 수 없는 이메일 입니다.");
            }
            return Map.of("result", "success", "status", "200");
        }

        return Map.of("result", "success", "status", "200");
    }
}
