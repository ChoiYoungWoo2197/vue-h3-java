package com.h3.h3_java.api.controller;

import com.h3.h3_java.api.service.SendGridService;
import com.h3.h3_java.raw.mongo.AdvMongoService;
import com.h3.h3_java.raw.mongo.EmailAuthMongoService;
import com.h3.h3_java.raw.mongo.UserMongoService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
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
    private final AdvMongoService       advMongo;
    private final EmailAuthMongoService emailAuthMongo;
    private final SendGridService       sendGrid;

    private static final DateTimeFormatter DT_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

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
    // 아이디/이메일 중복 확인
    // =============================================================================

    @PostMapping("/exists")
    public ResponseEntity<Map<String, Object>> exists(
            @RequestParam(defaultValue = "") String userid,
            @RequestParam(defaultValue = "") String useremail) {

        if (!userid.isBlank() && !useremail.isBlank()) {
            return failMsg("하나만 검색 가능합니다.", "1007");
        }
        if (!userid.isBlank()) {
            return userMongo.existsByUserId(userid)
                    ? failMsg("사용할 수 없는 아이디 입니다.", "1005") : ok();
        }
        if (!useremail.isBlank()) {
            return userMongo.existsByEmail(useremail)
                    ? failMsg("사용할 수 없는 이메일 입니다.", "1006") : ok();
        }
        return fail();
    }

    // =============================================================================
    // 마케터 목록 (select2 드롭다운용)
    // =============================================================================

    @PostMapping("/findmarketers")
    public ResponseEntity<Map<String, Object>> findMarketers(
            @RequestParam(defaultValue = "") String userlevel) {

        if (!"2".equals(userlevel)) {
            return failMsg("검색 결과가 없습니다.", "1004");
        }
        List<Document> list = userMongo.findMarketerList();
        Map<String, String> marketerMap = new LinkedHashMap<>();
        for (Document d : list) {
            marketerMap.put(d.getString("user_id"), d.getString("user_name"));
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("result",    "success");
        res.put("status",    "200");
        res.put("marketers", marketerMap);
        return ResponseEntity.ok(res);
    }

    // =============================================================================
    // 회원가입 (자체 신청 — user_status=0, 관리자 승인 대기)
    // =============================================================================

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(
            @RequestParam(defaultValue = "") String userid,
            @RequestParam(defaultValue = "") String userpass,
            @RequestParam(defaultValue = "") String username,
            @RequestParam(defaultValue = "") String usercompany,
            @RequestParam(defaultValue = "") String useremail,
            @RequestParam(defaultValue = "") String userphone,
            @RequestParam(defaultValue = "") String advmedia,
            @RequestParam(defaultValue = "") String advid,
            @RequestParam(defaultValue = "") String advmarketer) {

        if (userid.isBlank())      return failMsg("아이디를 확인해 주세요.", "1009");
        if (userpass.isBlank())    return failMsg("비밀번호를 확인해 주세요.", "1010");
        if (username.isBlank())    return failMsg("이름을 확인해 주세요.", "1011");
        if (usercompany.isBlank()) return failMsg("회사명을 확인해 주세요.", "1012");
        if (useremail.isBlank())   return failMsg("이메일을 확인해 주세요.", "1013");
        if (userphone.isBlank())   return failMsg("휴대번호를 확인해 주세요.", "1014");
        if (advmedia.isBlank())    return failMsg("매체를 확인해 주세요.", "1015");
        if (advid.isBlank())       return failMsg("광고 아이디를 확인해 주세요.", "1016");

        if (userMongo.existsByUserId(userid) || userMongo.existsByEmail(useremail)) {
            return failMsg("정상처리 되지 않았습니다.", "1002");
        }

        String now = LocalDateTime.now().format(DT_FMT);
        Document doc = new Document()
                .append("user_id",         userid)
                .append("user_pass",       sha256(userpass))
                .append("user_name",       username)
                .append("user_company",    usercompany)
                .append("user_email",      useremail)
                .append("user_phone",      userphone)
                .append("user_status",     0)
                .append("user_level",      0)
                .append("user_manager",    "H00408")
                .append("user_passupdate", now)
                .append("user_regdate",    now);
        userMongo.insertUser(doc);
        advMongo.insert(userid, advid, advmedia, advmarketer, now);

        log.info("[USER][REGISTER] 회원가입 신청 id={} media={}", userid, advmedia);
        return ok();
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

    private ResponseEntity<Map<String, Object>> failMsg(String msg, String status) {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("result",       "failed");
        res.put("status",       status);
        res.put("errormessage", msg);
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
