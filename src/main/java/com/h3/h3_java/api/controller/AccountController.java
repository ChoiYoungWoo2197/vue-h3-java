package com.h3.h3_java.api.controller;

import com.h3.h3_java.api.service.user.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/v1/h3/app/user")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    /**
     * 광고계정 CRUD
     * mode: L(조회) / I(등록) / U(수정) / D(삭제)
     * 프론트: javaAjax type:"post" → /app/user/account
     */
    @PostMapping("/account")
    public Map<String, Object> account(
            @RequestParam String userid,
            @RequestParam String media,
            @RequestParam(defaultValue = "L") String mode,
            @RequestParam(defaultValue = "") String naverid,
            @RequestParam(defaultValue = "") String navercustomer,
            @RequestParam(defaultValue = "") String naveraccess,
            @RequestParam(defaultValue = "") String naversecret,
            @RequestParam(defaultValue = "") String kakaosaid,
            @RequestParam(defaultValue = "") String naverdaid,
            @RequestParam(defaultValue = "") String kakaomomentid,
            @RequestParam(defaultValue = "") String googleid) {

        if (isBlank(userid) || isBlank(media)) {
            return Map.of("result", "failed", "status", "1009", "errormessage", "userid/media가 없습니다.");
        }

        switch (mode) {
            case "L":
                return accountService.getAccount(userid, media);
            case "I":
            case "U":
                return accountService.saveAccount(userid, media,
                        naverid, navercustomer, naveraccess, naversecret,
                        kakaosaid, naverdaid, kakaomomentid, googleid);
            case "D":
                return accountService.deleteAccount(userid, media);
            default:
                return Map.of("result", "failed", "status", "400", "errormessage", "지원하지 않는 mode입니다.");
        }
    }

    /**
     * 광고계정 유효성 검사 (저장 전 API 호출로 확인)
     * 프론트: javaAjax type:"post" → /app/user/accountValidate
     */
    @PostMapping("/accountValidate")
    public Map<String, Object> accountValidate(
            @RequestParam String userid,
            @RequestParam String media,
            @RequestParam(defaultValue = "") String navercustomer,
            @RequestParam(defaultValue = "") String naveraccess,
            @RequestParam(defaultValue = "") String naversecret,
            @RequestParam(defaultValue = "") String kakaosaid,
            @RequestParam(defaultValue = "") String naverdaid,
            @RequestParam(defaultValue = "") String kakaomomentid,
            @RequestParam(defaultValue = "") String googleid) {

        if (isBlank(userid) || isBlank(media)) {
            return Map.of("result", "fail", "status", "400",
                    "data", Map.of("message", "userid/media가 없습니다."));
        }

        return accountService.validateAccount(media,
                navercustomer, naveraccess, naversecret,
                kakaosaid, naverdaid, kakaomomentid, googleid);
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
