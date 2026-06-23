package com.h3.h3_java.api.controller;

import com.h3.h3_java.api.service.admin.AdminUserService;
import com.h3.h3_java.auth.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    // 마케터 목록
    @GetMapping("/marketers")
    public Map<String, Object> getMarketerList(
            @RequestHeader("Authorization") String auth,
            @RequestParam(defaultValue = "")         String query,
            @RequestParam(defaultValue = "username") String field,
            @RequestParam(defaultValue = "0")        int    start,
            @RequestParam(defaultValue = "100")      int    display,
            @RequestParam(defaultValue = "dd")       String sort) {
        return adminUserService.getMarketerList(query, field, start, display, sort);
    }

    // 광고주 목록 (계정이동용)
    @GetMapping("/member-users")
    public Map<String, Object> getMemberUsers(
            @RequestHeader("Authorization") String auth,
            @RequestParam(defaultValue = "")            String query,
            @RequestParam(defaultValue = "usercompany") String field,
            @RequestParam(defaultValue = "")            String manager,
            @RequestParam(defaultValue = "0")           int    start,
            @RequestParam(defaultValue = "10")          int    display,
            @RequestParam(defaultValue = "dd")          String sort) {
        return adminUserService.getMemberUsers(query, field, manager, start, display, sort);
    }

    // 가입승인 목록
    @GetMapping("/membership-users")
    public Map<String, Object> getMembershipUsers(
            @RequestHeader("Authorization") String auth,
            @RequestParam(defaultValue = "username") String field,
            @RequestParam(defaultValue = "")         String query,
            @RequestParam(defaultValue = "0")        int    start,
            @RequestParam(defaultValue = "10")       int    display,
            @RequestParam(defaultValue = "dd")       String sort) {
        return adminUserService.getMembershipUsers(field, query, start, display, sort);
    }

    // 가입 상태 변경 (승인/보류/거절)
    @PostMapping("/userstatus")
    public Map<String, Object> updateMemberStatus(
            @RequestHeader("Authorization") String auth,
            @RequestParam String applyuserid,
            @RequestParam(defaultValue = "") String manageruserid,
            @RequestParam int    userstatus) {
        return adminUserService.updateMemberStatus(applyuserid, userstatus, manageruserid);
    }

    // 계정이동
    @PostMapping("/usertransfer")
    public Map<String, Object> transferUsers(
            @RequestHeader("Authorization") String auth,
            @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> applyUserIds  = (List<String>) body.get("applyuserid");
        String       managerUserId = (String) body.get("manageruserid");
        return adminUserService.transferUsers(applyUserIds, managerUserId);
    }
}
