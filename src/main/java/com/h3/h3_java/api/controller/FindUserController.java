package com.h3.h3_java.api.controller;

import com.h3.h3_java.api.service.SendGridService;
import com.h3.h3_java.raw.mongo.EmailAuthMongoService;
import com.h3.h3_java.raw.mongo.UserMongoService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * PHP user/emailauthnoforid, findid, emailauthnoforpw, findpass 이식.
 * /v1/h3/user/** → anyRequest().permitAll() 로 JWT 없이 접근 가능.
 */
@Slf4j
@RestController
@RequestMapping("/v1/h3/user")
@RequiredArgsConstructor
public class FindUserController {

    private final UserMongoService      userMongo;
    private final EmailAuthMongoService emailAuthMongo;
    private final SendGridService       sendGrid;

    // =============================================================================
    // 아이디 찾기 — 인증코드 발송
    // =============================================================================

    @PostMapping("/emailauthnoforid")
    public ResponseEntity<Map<String, Object>> emailAuthNoForId(
            @RequestParam String username,
            @RequestParam String useremail,
            HttpServletRequest req) {

        Document user = userMongo.findByUserNameAndEmail(username, useremail);
        if (user == null) {
            log.info("[USER][FINDID][AUTH] 사용자 없음 name={} email={}", username, useremail);
            return fail();
        }

        String code = randomString(10);
        emailAuthMongo.upsertIdLog(username, useremail, code, req.getRemoteAddr());

        String html = sendGrid.buildAuthCodeHtml(
            "인증코드",
            "인증코드를 보내드리오며<br>자세한 내용이나 기타 문의사항은 마케터에게 문의 주시면 최대한 빠르게 안내드리겠습니다.",
            "인증코드: " + code
        );
        boolean sent = sendGrid.send(useremail, username, "희일커뮤니케이션", html);
        log.info("[USER][FINDID][AUTH] 인증코드 발송 name={} sent={}", username, sent);
        return sent ? ok() : fail();
    }

    // =============================================================================
    // 아이디 찾기 — 코드 검증 후 아이디 이메일 발송
    // =============================================================================

    @PostMapping("/findid")
    public ResponseEntity<Map<String, Object>> findId(
            @RequestParam String username,
            @RequestParam String useremail,
            @RequestParam String code) {

        Document authLog = emailAuthMongo.findIdLog(username, useremail, code);
        if (authLog == null) {
            log.info("[USER][FINDID] 코드 불일치 name={} email={}", username, useremail);
            return fail();
        }

        Document user = userMongo.findByUserNameAndEmail(username, useremail);
        if (user == null) return fail();

        String userId = user.getString("user_id");
        String html = sendGrid.buildAuthCodeHtml(
            "아이디 찾기",
            "아이디를 보내드리오며<br>자세한 내용이나 기타 문의사항은 마케터에게 문의 주시면 최대한 빠르게 안내드리겠습니다.",
            "아이디: " + userId
        );
        boolean sent = sendGrid.send(useremail, username, "희일커뮤니케이션", html);
        log.info("[USER][FINDID] 아이디 발송 name={} sent={}", username, sent);
        return sent ? ok() : fail();
    }

    // =============================================================================
    // 비밀번호 찾기 — 인증코드 발송
    // =============================================================================

    @PostMapping("/emailauthnoforpw")
    public ResponseEntity<Map<String, Object>> emailAuthNoForPw(
            @RequestParam String userid,
            @RequestParam String useremail,
            HttpServletRequest req) {

        Document user = userMongo.findByUserIdAndEmail(userid, useremail);
        if (user == null) {
            log.info("[USER][FINDPW][AUTH] 사용자 없음 id={} email={}", userid, useremail);
            return fail();
        }

        String code = randomString(10);
        emailAuthMongo.upsertPwLog(userid, useremail, code, req.getRemoteAddr());

        String html = sendGrid.buildAuthCodeHtml(
            "인증코드",
            "인증코드를 보내드리오며<br>자세한 내용이나 기타 문의사항은 마케터에게 문의 주시면 최대한 빠르게 안내드리겠습니다.",
            "인증코드: " + code
        );
        boolean sent = sendGrid.send(useremail, userid, "희일커뮤니케이션", html);
        log.info("[USER][FINDPW][AUTH] 인증코드 발송 id={} sent={}", userid, sent);
        return sent ? ok() : fail();
    }

    // =============================================================================
    // 비밀번호 찾기 — 코드 검증 후 임시 비밀번호 발급
    // =============================================================================

    @PostMapping("/findpass")
    public ResponseEntity<Map<String, Object>> findPass(
            @RequestParam String userid,
            @RequestParam String useremail,
            @RequestParam String code) {

        Document authLog = emailAuthMongo.findPwLog(userid, useremail, code);
        if (authLog == null) {
            log.info("[USER][FINDPW] 코드 불일치 id={} email={}", userid, useremail);
            return fail();
        }

        Document user = userMongo.findByUserIdAndEmail(userid, useremail);
        if (user == null) return fail();

        String tempPw     = randomString(10);
        String hashedPass = sha256(tempPw);
        userMongo.updateUserPassByIdAndEmail(userid, useremail, hashedPass);

        String userName = user.getString("user_name");
        String html = sendGrid.buildAuthCodeHtml(
            "비밀번호 찾기",
            "임시 비밀번호를 보내드리오며<br>자세한 내용이나 기타 문의사항은 마케터에게 문의 주시면 최대한 빠르게 안내드리겠습니다.",
            "임시 비밀번호: " + tempPw
        );
        boolean sent = sendGrid.send(useremail, userName != null ? userName : userid, "희일커뮤니케이션", html);
        log.info("[USER][FINDPW] 임시 비밀번호 발송 id={} sent={}", userid, sent);
        return sent ? ok() : fail();
    }

    // =============================================================================
    // 공통
    // =============================================================================

    private ResponseEntity<Map<String, Object>> ok() {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("result", "success");
        res.put("status", "200");
        return ResponseEntity.ok(res);
    }

    private ResponseEntity<Map<String, Object>> fail() {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("result", "failed");
        res.put("status", "1020");
        return ResponseEntity.ok(res);
    }

    private String randomString(int length) {
        String chars = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt((int) (Math.random() * chars.length())));
        }
        return sb.toString();
    }

    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 오류", e);
        }
    }
}
