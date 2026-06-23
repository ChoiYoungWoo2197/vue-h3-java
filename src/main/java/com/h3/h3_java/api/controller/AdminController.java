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

    // 내 광고주 목록
    @GetMapping("/my-users")
    public Map<String, Object> getMyUsers(
            @RequestHeader("Authorization") String auth,
            @RequestParam(defaultValue = "")         String query,
            @RequestParam(defaultValue = "username") String field,
            @RequestParam(defaultValue = "fa")       String sort,
            @RequestParam(defaultValue = "0")        int    start,
            @RequestParam(defaultValue = "10")       int    display) {
        Claims claims   = jwtUtil.parseClaims(auth.substring(7));
        String callerId = claims.getSubject();
        int callerLevel = claims.get("userLevel", Integer.class);
        return adminUserService.getMyUsers(callerId, callerLevel, query, field, sort, start, display);
    }

    // 공유받은 광고주 목록
    @GetMapping("/beshared-users")
    public Map<String, Object> getBeSharedUsers(
            @RequestHeader("Authorization") String auth,
            @RequestParam(defaultValue = "")         String query,
            @RequestParam(defaultValue = "username") String field,
            @RequestParam(defaultValue = "fa")       String sort,
            @RequestParam(defaultValue = "0")        int    start,
            @RequestParam(defaultValue = "10")       int    display) {
        Claims claims   = jwtUtil.parseClaims(auth.substring(7));
        String callerId = claims.getSubject();
        int callerLevel = claims.get("userLevel", Integer.class);
        return adminUserService.getBeSharedUsers(callerId, callerLevel, query, field, sort, start, display);
    }

    // 즐겨찾기 토글
    @PostMapping("/favorites")
    public Map<String, Object> updateFavorites(
            @RequestParam String applyuserid,
            @RequestParam String favorites) {
        return adminUserService.updateFavorites(applyuserid, favorites);
    }

    // 바로가기 (target user JWT 발급)
    @PostMapping("/userlink")
    public Map<String, Object> getUserLink(
            @RequestParam String userid) {
        return adminUserService.getUserLink(userid);
    }

    // 공유 마케터 업데이트
    @PostMapping("/share-update")
    public Map<String, Object> updateShareManagers(
            @RequestBody Map<String, Object> body) {
        String applyuserid = (String) body.get("applyuserid");
        @SuppressWarnings("unchecked")
        List<String> shareManagers = (List<String>) body.get("sharemanager");
        return adminUserService.updateShareManagers(applyuserid, shareManagers);
    }

    // 광고주 등록
    @PostMapping("/userregister")
    public Map<String, Object> registerUser(
            @RequestHeader("Authorization") String auth,
            @RequestParam String userid,
            @RequestParam String userpass,
            @RequestParam String username,
            @RequestParam String usercompany,
            @RequestParam(defaultValue = "") String advmarketer) {
        return adminUserService.registerUser(userid, userpass, username, usercompany, advmarketer);
    }

    // 에이전시 광고주 목록 (내 광고주 + 일별 비용)
    @GetMapping("/agency-users")
    public Map<String, Object> getAgencyUsers(
            @RequestHeader("Authorization") String auth,
            @RequestParam(defaultValue = "")         String query,
            @RequestParam(defaultValue = "userid")   String field,
            @RequestParam(defaultValue = "nd")       String sort,
            @RequestParam(defaultValue = "0")        int    start,
            @RequestParam(defaultValue = "10")       int    display,
            @RequestParam(defaultValue = "")         String fromdate,
            @RequestParam(defaultValue = "")         String todate) {
        Claims claims   = jwtUtil.parseClaims(auth.substring(7));
        String callerId = claims.getSubject();
        int callerLevel = claims.get("userLevel", Integer.class);
        String from = fromdate.isBlank() ? null : fromdate;
        String to   = todate.isBlank()   ? null : todate;
        return adminUserService.getAgencyUsers(callerId, callerLevel, query, field, sort, start, display, from, to);
    }

    // 에이전시 공유받은 광고주 목록 + 일별 비용
    @GetMapping("/agency-beshared-users")
    public Map<String, Object> getAgencyBeSharedUsers(
            @RequestHeader("Authorization") String auth,
            @RequestParam(defaultValue = "")         String query,
            @RequestParam(defaultValue = "userid")   String field,
            @RequestParam(defaultValue = "nd")       String sort,
            @RequestParam(defaultValue = "0")        int    start,
            @RequestParam(defaultValue = "10")       int    display,
            @RequestParam(defaultValue = "")         String fromdate,
            @RequestParam(defaultValue = "")         String todate) {
        Claims claims   = jwtUtil.parseClaims(auth.substring(7));
        String callerId = claims.getSubject();
        int callerLevel = claims.get("userLevel", Integer.class);
        String from = fromdate.isBlank() ? null : fromdate;
        String to   = todate.isBlank()   ? null : todate;
        return adminUserService.getAgencyBeSharedUsers(callerId, callerLevel, query, field, sort, start, display, from, to);
    }

    // 에이전시 통합 알림
    @GetMapping("/agency-alarms")
    public Map<String, Object> getAgencyAlarms(
            @RequestHeader("Authorization") String auth,
            @RequestParam(defaultValue = "0")  int start,
            @RequestParam(defaultValue = "5")  int display) {
        Claims claims   = jwtUtil.parseClaims(auth.substring(7));
        String callerId = claims.getSubject();
        int callerLevel = claims.get("userLevel", Integer.class);
        return adminUserService.getAgencyAlarms(callerId, callerLevel, start, display);
    }

    // 계정 등록 (없을 때만 INSERT)
    @PostMapping("/account-register")
    public Map<String, Object> registerAccount(
            @RequestParam String userid,
            @RequestParam(defaultValue = "") String naverid,
            @RequestParam(defaultValue = "") String navercustomer,
            @RequestParam(defaultValue = "") String naveraccess,
            @RequestParam(defaultValue = "") String naversecret,
            @RequestParam(defaultValue = "") String naverdaid,
            @RequestParam(defaultValue = "") String kakaosaid,
            @RequestParam(defaultValue = "") String kakaomomentid,
            @RequestParam(defaultValue = "") String googleid) {
        return adminUserService.registerAccount(userid, naverid, navercustomer, naveraccess,
                                                naversecret, naverdaid, kakaosaid, kakaomomentid, googleid);
    }

}
