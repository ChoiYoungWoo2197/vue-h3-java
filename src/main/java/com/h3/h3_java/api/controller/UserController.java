package com.h3.h3_java.api.controller;

import com.h3.h3_java.api.service.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/v1/h3/app/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** 회원정보 수정 (DashboardEdit.vue) */
    @PostMapping("/userupdate")
    public Map<String, Object> userupdate(
            @RequestParam String userid,
            @RequestParam(required = false, defaultValue = "") String userpass,
            @RequestParam String username,
            @RequestParam String usercompany,
            @RequestParam String useremail,
            @RequestParam String userphone) {

        if (userid.isBlank() || username.isBlank() || usercompany.isBlank()
                || useremail.isBlank() || userphone.isBlank()) {
            return Map.of("result", "failed", "status", "1009",
                    "errormessage", "필수 항목을 확인해주세요.");
        }
        return userService.updateUser(userid, userpass, username, usercompany, useremail, userphone);
    }

    /** 이메일 중복 체크 (DashboardEdit.vue) */
    @PostMapping("/exists")
    public Map<String, Object> exists(
            @RequestParam(required = false, defaultValue = "") String currentemail,
            @RequestParam(required = false, defaultValue = "") String useremail) {

        return userService.checkEmail(currentemail, useremail);
    }

    /** h3_users MySQL → MongoDB 일회성 이관 */
    @PostMapping("/migrate-users")
    public Map<String, Object> migrateUsers() {
        return userService.migrateUsers();
    }
}
