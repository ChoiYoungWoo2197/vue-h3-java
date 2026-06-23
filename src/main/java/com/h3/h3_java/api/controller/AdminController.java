package com.h3.h3_java.api.controller;

import com.h3.h3_java.api.service.admin.AdminUserService;
import com.h3.h3_java.auth.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/v1/h3/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminUserService adminUserService;
    private final JwtUtil          jwtUtil;

    // 에이전트 리스트
    @GetMapping("/agent")
    public Map<String, Object> getAgentList(
            @RequestHeader("Authorization") String auth,
            @RequestParam(defaultValue = "")         String query,
            @RequestParam(defaultValue = "username") String field,
            @RequestParam(defaultValue = "0")        int    start,
            @RequestParam(defaultValue = "10")       int    display,
            @RequestParam(defaultValue = "dd")       String sort) {

        Claims claims   = jwtUtil.parseClaims(auth.substring(7));
        String callerId = claims.getSubject();
        int callerLevel = claims.get("userLevel", Integer.class);

        return adminUserService.getAgentList(callerId, callerLevel, query, field, start, display, sort);
    }

    // 에이전트 상태 변경 (승인/미승인)
    @PostMapping("/agent/status")
    public Map<String, Object> updateUserStatus(
            @RequestParam String userid,
            @RequestParam int    status) {
        return adminUserService.updateUserStatus(userid, status);
    }
}
