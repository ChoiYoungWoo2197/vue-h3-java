package com.h3.h3_java.api.service.admin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.h3.h3_java.api.mapper.AdminMapper;
import com.h3.h3_java.raw.mongo.AccountMongoService;
import com.h3.h3_java.raw.mongo.ShareMongoService;
import com.h3.h3_java.raw.mongo.UserMongoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserMongoService   userMongo;
    private final ShareMongoService  shareMongo;
    private final AccountMongoService accountMongo;
    private final AdminMapper        adminMapper;

    public Map<String, Object> getAgentList(String callerId, int callerLevel,
                                             String query, String field,
                                             int start, int display, String sort) {
        boolean desc = sort == null || !sort.endsWith("a");
        int skip = start * display;

        List<Document> docs = userMongo.findAgents(callerLevel, callerId, field, query, skip, display, desc);
        long total          = userMongo.countAgents(callerLevel, callerId, field, query);

        List<Map<String, Object>> users = new ArrayList<>();
        for (Document d : docs) {
            String userId = d.getString("user_id");

            int userCount  = userId != null ? shareMongo.countByUserManager(userId) : 0;
            int shareCount = userId != null ? shareMongo.countByShareManager(userId) : 0;

            Map<String, Object> u = new LinkedHashMap<>();
            u.put("usersel",     d.get("user_seq"));
            u.put("userid",      userId);
            u.put("username",    d.getString("user_name"));
            u.put("usercompany", d.getString("user_company"));
            u.put("useremail",   d.getString("user_email"));
            u.put("userphone",   d.getString("user_phone"));
            u.put("userstatus",  parseIntSafe(d.get("user_status"), 0));
            u.put("userlevel",   d.getInteger("user_level", 0));
            u.put("userregdate", d.getString("user_regdate"));
            u.put("usercount",   userCount);
            u.put("sharecount",  shareCount);
            users.add(u);
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("result",      "success");
        res.put("status",      "200");
        res.put("users",       users);
        res.put("totalcount",  total);
        res.put("resultcount", users.size());
        return res;
    }

    // ── 마케터 목록 ────────────────────────────────────────────────────────────

    public Map<String, Object> getMarketerList(String query, String field, int start, int display, String sort) {
        boolean desc = sort == null || !sort.endsWith("a");
        int skip = start * display;

        List<Document> docs = userMongo.findMarketers(field, query, skip, display, desc);
        long total          = userMongo.countMarketers(field, query);

        Map<String, String> marketers = new LinkedHashMap<>();
        for (Document d : docs) {
            String userId   = d.getString("user_id");
            String userName = d.getString("user_name");
            if (userId != null) marketers.put(userId, userName != null ? userName : "");
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("result",      "success");
        res.put("status",      "200");
        res.put("marketers",   marketers);
        res.put("totalcount",  total);
        res.put("resultcount", docs.size());
        return res;
    }

    // ── 광고주 목록 (계정이동용) ────────────────────────────────────────────────

    public Map<String, Object> getMemberUsers(String query, String field, String manager,
                                              int start, int display, String sort) {
        boolean desc = sort == null || !sort.endsWith("a");
        int skip = start * display;

        List<Document> docs = userMongo.findLevel1Users(field, query, manager, skip, display, desc);
        long total          = userMongo.countLevel1Users(field, query, manager);

        List<Map<String, Object>> users = new ArrayList<>();
        for (Document d : docs) {
            String managerId   = d.getString("user_manager");
            String managerName = "";
            if (managerId != null && !managerId.isBlank()) {
                Document mDoc = userMongo.findByUserId(managerId);
                if (mDoc != null) managerName = mDoc.getString("user_name") != null ? mDoc.getString("user_name") : "";
            }

            Map<String, Object> u = new LinkedHashMap<>();
            u.put("usersel",         d.get("user_seq"));
            u.put("userid",          d.getString("user_id"));
            u.put("username",        d.getString("user_name"));
            u.put("usercompany",     d.getString("user_company"));
            u.put("useremail",       d.getString("user_email"));
            u.put("userphone",       d.getString("user_phone"));
            u.put("userstatus",      parseIntSafe(d.get("user_status"), 0));
            u.put("userlevel",       d.getInteger("user_level", 0));
            u.put("usermanagername", managerName);
            u.put("usermanager",     managerId != null ? managerId : "");
            u.put("userregdate",     d.getString("user_regdate"));
            users.add(u);
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("result",      "success");
        res.put("status",      "200");
        res.put("users",       users);
        res.put("totalcount",  total);
        res.put("resultcount", users.size());
        return res;
    }

    // ── 계정이동 ────────────────────────────────────────────────────────────────

    public Map<String, Object> transferUsers(List<String> applyUserIds, String managerUserId) {
        int count = 0;
        for (String userId : applyUserIds) {
            Document user = userMongo.findByUserId(userId);
            if (user != null) {
                userMongo.updateManager(userId, managerUserId);
                shareMongo.updateUserManager(userId, managerUserId);
                count++;
            }
        }

        Map<String, Object> res = new LinkedHashMap<>();
        if (count > 0) {
            res.put("result",      "success");
            res.put("status",      "200");
            res.put("resultcount", count);
        } else {
            res.put("result",       "failed");
            res.put("status",       "1002");
            res.put("errormessage", "정상처리 되지 않았습니다.");
        }
        return res;
    }

    // ── 가입승인 목록 ───────────────────────────────────────────────────────────

    public Map<String, Object> getMembershipUsers(String field, String query, int start, int display, String sort) {
        boolean desc = sort == null || !sort.endsWith("a");
        int skip = start * display;

        // advid 검색: h3_account에서 user_id 목록 먼저 조회
        List<String> advUserIds = null;
        if ("advid".equals(field) && query != null && !query.isBlank()) {
            advUserIds = accountMongo.findUserIdsByAdvId(query);
            if (advUserIds.isEmpty()) {
                return emptyUserResult();
            }
        }

        List<Document> docs = userMongo.findMembershipUsers(field, query, advUserIds, skip, display, desc);
        long total          = userMongo.countMembershipUsers(field, query, advUserIds);

        // h3_account 배치 로드 (N+1 방지)
        List<String> userIds = docs.stream()
            .map(d -> d.getString("user_id")).filter(Objects::nonNull).collect(Collectors.toList());
        Map<String, Document> accountMap = accountMongo.findByUserIds(userIds).stream()
            .collect(Collectors.toMap(d -> d.getString("user_id"), d -> d, (a, b) -> a));

        List<Map<String, Object>> users = new ArrayList<>();
        for (Document d : docs) {
            String userId    = d.getString("user_id");
            String managerId = d.getString("user_manager");

            String managerName = "";
            if (managerId != null && !managerId.isBlank()) {
                Document mDoc = userMongo.findByUserId(managerId);
                if (mDoc != null && mDoc.getString("user_name") != null)
                    managerName = mDoc.getString("user_name");
            }

            String[] adv = deriveAdvInfo(accountMap.get(userId));

            Map<String, Object> u = new LinkedHashMap<>();
            u.put("usersel",         d.get("user_seq"));
            u.put("userid",          userId);
            u.put("username",        d.getString("user_name"));
            u.put("usercompany",     d.getString("user_company"));
            u.put("useremail",       d.getString("user_email"));
            u.put("userphone",       d.getString("user_phone"));
            u.put("userstatus",      parseIntSafe(d.get("user_status"), 0));
            u.put("userlevel",       d.getInteger("user_level", 0));
            u.put("usermanagername", managerName);
            u.put("usermanager",     managerId != null ? managerId : "");
            u.put("userregdate",     d.getString("user_regdate"));
            u.put("advmedia",        adv[0]);
            u.put("advid",           adv[1]);
            users.add(u);
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("result",      "success");
        res.put("status",      "200");
        res.put("users",       users);
        res.put("totalcount",  total);
        res.put("resultcount", users.size());
        return res;
    }

    // ── 가입 상태 변경 (승인/보류/거절) ────────────────────────────────────────

    public Map<String, Object> updateMemberStatus(String targetUserId, int status, String managerUserId) {
        int userLevel = (status == 1) ? 1 : 0;
        userMongo.updateUserStatusAndLevel(targetUserId, status, userLevel, managerUserId);

        if (status == 1 && managerUserId != null && !managerUserId.isBlank()) {
            String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            shareMongo.upsert(targetUserId, managerUserId, List.of(managerUserId), 0, now);
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("result", "success");
        res.put("status", "200");
        return res;
    }

    private String[] deriveAdvInfo(Document acct) {
        if (acct == null) return new String[]{"", ""};
        if (notBlank(acct.getString("account_naver")))       return new String[]{"N",      acct.getString("account_naver")};
        if (notBlank(acct.getString("account_gfa")))         return new String[]{"Nda",    acct.getString("account_gfa")};
        if (notBlank(acct.getString("account_kakaosa")))     return new String[]{"D",      acct.getString("account_kakaosa")};
        if (notBlank(acct.getString("account_kakaomoment"))) return new String[]{"K",      acct.getString("account_kakaomoment")};
        if (notBlank(acct.getString("account_google")))      return new String[]{"google", acct.getString("account_google")};
        return new String[]{"", ""};
    }

    private boolean notBlank(String s) { return s != null && !s.isBlank(); }

    private Map<String, Object> emptyUserResult() {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("result",      "success");
        res.put("status",      "200");
        res.put("users",       List.of());
        res.put("totalcount",  0L);
        res.put("resultcount", 0);
        return res;
    }

    public Map<String, Object> updateUserStatus(String targetUserId, int status) {
        userMongo.updateUserStatus(targetUserId, status);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("result", "success");
        res.put("status", "200");
        return res;
    }

    // ── h3_share MySQL → MongoDB 일회성 이관 ────────────────────────────────────

    public Map<String, Object> migrateShare() {
        List<Map<String, Object>> rows = adminMapper.selectAllShare();
        ObjectMapper om = new ObjectMapper();
        int ok = 0, skip = 0;
        for (Map<String, Object> row : rows) {
            try {
                String userId      = objStr(row.get("user_id"));
                String userManager = objStr(row.get("user_manager"));
                String smJson      = objStr(row.get("share_manager"));
                String regdate     = objStr(row.get("share_regdate"));
                int    favorites   = parseIntSafe(row.get("share_favorites"), 0);

                List<String> shareManager = new ArrayList<>();
                if (smJson != null && smJson.startsWith("[")) {
                    shareManager = om.readValue(smJson, new TypeReference<>() {});
                }

                shareMongo.upsert(userId, userManager, shareManager, favorites, regdate);
                ok++;
            } catch (Exception e) {
                log.warn("[SHARE-MIGRATE] 실패: {} - {}", row.get("user_id"), e.getMessage());
                skip++;
            }
        }
        return Map.of("result", "success", "migrated", ok, "skipped", skip);
    }

    private String objStr(Object v) {
        return v == null ? null : v.toString();
    }

    private int parseIntSafe(Object val, int def) {
        if (val == null) return def;
        if (val instanceof Number n) return n.intValue();
        try { return Integer.parseInt(val.toString()); } catch (Exception e) { return def; }
    }
}
