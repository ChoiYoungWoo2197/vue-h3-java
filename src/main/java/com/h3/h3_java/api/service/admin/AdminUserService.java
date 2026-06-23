package com.h3.h3_java.api.service.admin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.h3.h3_java.api.mapper.AdminMapper;
import com.h3.h3_java.auth.JwtUtil;
import com.h3.h3_java.raw.mongo.AccountMongoService;
import com.h3.h3_java.raw.mongo.AdvMongoService;
import com.h3.h3_java.raw.mongo.ShareMongoService;
import com.h3.h3_java.raw.mongo.UserMongoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserMongoService    userMongo;
    private final ShareMongoService   shareMongo;
    private final AccountMongoService accountMongo;
    private final AdvMongoService     advMongo;
    private final AdminMapper         adminMapper;
    private final JwtUtil             jwtUtil;

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

    // ── h3_adv MySQL → MongoDB 일회성 이관 ─────────────────────────────────────

    public Map<String, Object> migrateAdv() {
        List<Map<String, Object>> rows = adminMapper.selectAllAdv();
        int ok = 0, skip = 0;
        for (Map<String, Object> row : rows) {
            try {
                String userId              = objStr(row.get("user_id"));
                String advId               = objStr(row.get("adv_id"));
                String advMedia            = objStr(row.get("adv_media"));
                String advRegdate          = objStr(row.get("adv_regdate"));
                String advMarketer         = objStr(row.get("adv_marketer"));
                String advMarketerRegdate  = objStr(row.get("adv_marketer_regdate"));
                int    advSalseDay         = parseIntSafe(row.get("adv_salse_day"), 0);

                advMongo.upsert(userId,
                    advId               != null ? advId               : "",
                    advMedia            != null ? advMedia            : "",
                    advRegdate          != null ? advRegdate          : "",
                    advMarketer         != null ? advMarketer         : "",
                    advMarketerRegdate  != null ? advMarketerRegdate  : "",
                    advSalseDay);
                ok++;
            } catch (Exception e) {
                log.warn("[ADV-MIGRATE] 실패: {} - {}", row.get("user_id"), e.getMessage());
                skip++;
            }
        }
        return Map.of("result", "success", "migrated", ok, "skipped", skip);
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

    // ── 내 광고주 목록 ─────────────────────────────────────────────────────────

    public Map<String, Object> getMyUsers(String callerId, int callerLevel,
                                          String query, String field, String sort,
                                          int start, int display) {
        int skip = start * display;
        List<Document> docs = userMongo.findMyUsers(callerLevel, callerId, field, query, skip, display);
        long total          = userMongo.countMyUsers(callerLevel, callerId, field, query);

        List<String> userIds = docs.stream()
            .map(d -> d.getString("user_id")).filter(Objects::nonNull).collect(Collectors.toList());
        Map<String, Document> shareMap = shareMongo.findByUserIds(userIds).stream()
            .collect(Collectors.toMap(d -> d.getString("user_id"), d -> d, (a, b) -> a));
        Map<String, Document> advMap = advMongo.findByUserIds(userIds).stream()
            .collect(Collectors.toMap(d -> d.getString("user_id"), d -> d, (a, b) -> a));
        Map<String, Document> marketerCache = new HashMap<>();

        List<Map<String, Object>> users = new ArrayList<>();
        for (Document d : docs) {
            String userId    = d.getString("user_id");
            String managerId = d.getString("user_manager");
            Document share   = shareMap.get(userId);
            Document adv     = advMap.get(userId);

            Map<String, String> shareObj = new LinkedHashMap<>();
            if (share != null) {
                Object sm = share.get("share_manager");
                if (sm instanceof List<?> smList) {
                    for (Object mid : smList) {
                        String midStr = mid.toString();
                        Document mDoc = marketerCache.computeIfAbsent(midStr, userMongo::findByUserId);
                        shareObj.put(midStr, mDoc != null ? mDoc.getString("user_name") : midStr);
                    }
                }
            }

            String managerName = "";
            if (managerId != null && !managerId.isBlank()) {
                Document mDoc = marketerCache.computeIfAbsent(managerId, userMongo::findByUserId);
                if (mDoc != null && mDoc.getString("user_name") != null) managerName = mDoc.getString("user_name");
            }

            String advId    = adv != null ? adv.getString("adv_id")    : null;
            String advMedia = adv != null ? adv.getString("adv_media")  : null;
            int    salse    = adv != null ? parseIntSafe(adv.get("adv_salse_day"), 0) : 0;

            Map<String, Object> u = new LinkedHashMap<>();
            u.put("usersel",         d.get("user_seq"));
            u.put("userid",          userId);
            u.put("username",        d.getString("user_name"));
            u.put("usercompany",     d.getString("user_company"));
            u.put("useremail",       d.getString("user_email"));
            u.put("userphone",       d.getString("user_phone"));
            u.put("userstatus",      parseIntSafe(d.get("user_status"), 0));
            u.put("userlevel",       d.getInteger("user_level", 0));
            u.put("favorites",       share != null ? share.get("share_favorites") : "n");
            u.put("usermanagername", managerName);
            u.put("usermanager",     managerId != null ? managerId : "");
            u.put("advid",           advId);
            u.put("advmedia",        advMedia);
            u.put("salse",           salse);
            u.put("share",           shareObj.isEmpty() ? null : shareObj);
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

    // ── 공유받은 광고주 목록 ───────────────────────────────────────────────────

    public Map<String, Object> getBeSharedUsers(String callerId, int callerLevel,
                                                 String query, String field, String sort,
                                                 int start, int display) {
        List<String> sharedUserIds = shareMongo.findUserIdsByShareManager(callerId);
        if (sharedUserIds.isEmpty()) return emptyUserResult();

        int skip = start * display;
        List<Document> docs = userMongo.findUsersByIds(sharedUserIds, field, query, skip, display);
        long total          = userMongo.countUsersByIds(sharedUserIds, field, query);

        List<String> pageUserIds = docs.stream()
            .map(d -> d.getString("user_id")).filter(Objects::nonNull).collect(Collectors.toList());
        Map<String, Document> shareMap = shareMongo.findByUserIds(pageUserIds).stream()
            .collect(Collectors.toMap(d -> d.getString("user_id"), d -> d, (a, b) -> a));
        Map<String, Document> advMap = advMongo.findByUserIds(pageUserIds).stream()
            .collect(Collectors.toMap(d -> d.getString("user_id"), d -> d, (a, b) -> a));
        Map<String, Document> marketerCache = new HashMap<>();

        List<Map<String, Object>> users = new ArrayList<>();
        for (Document d : docs) {
            String userId    = d.getString("user_id");
            String managerId = d.getString("user_manager");
            Document share   = shareMap.get(userId);
            Document adv     = advMap.get(userId);

            String managerName = "";
            if (managerId != null && !managerId.isBlank()) {
                Document mDoc = marketerCache.computeIfAbsent(managerId, userMongo::findByUserId);
                if (mDoc != null && mDoc.getString("user_name") != null) managerName = mDoc.getString("user_name");
            }

            String advId    = adv != null ? adv.getString("adv_id")    : null;
            String advMedia = adv != null ? adv.getString("adv_media")  : null;
            int    salse    = adv != null ? parseIntSafe(adv.get("adv_salse_day"), 0) : 0;

            Map<String, Object> u = new LinkedHashMap<>();
            u.put("usersel",         d.get("user_seq"));
            u.put("userid",          userId);
            u.put("username",        d.getString("user_name"));
            u.put("usercompany",     d.getString("user_company"));
            u.put("useremail",       d.getString("user_email"));
            u.put("userphone",       d.getString("user_phone"));
            u.put("userstatus",      parseIntSafe(d.get("user_status"), 0));
            u.put("userlevel",       d.getInteger("user_level", 0));
            u.put("favorites",       share != null ? share.get("share_favorites") : "n");
            u.put("usermanagername", managerName);
            u.put("usermanager",     managerId != null ? managerId : "");
            u.put("advid",           advId);
            u.put("advmedia",        advMedia);
            u.put("salse",           salse);
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

    // ── 즐겨찾기 토글 ─────────────────────────────────────────────────────────

    public Map<String, Object> updateFavorites(String applyuserid, String favorites) {
        boolean updated = shareMongo.updateFavorites(applyuserid, favorites);
        Map<String, Object> res = new LinkedHashMap<>();
        if (updated) {
            res.put("result", "success");
            res.put("status", "200");
        } else {
            res.put("result",       "failed");
            res.put("status",       "0004");
            res.put("errormessage", "중복처리로 적용되지 않았습니다.");
        }
        res.put("resultcount", updated ? 1 : 0);
        return res;
    }

    // ── 바로가기 (target user JWT 발급) ───────────────────────────────────────

    public Map<String, Object> getUserLink(String userid) {
        Document user = userMongo.findByUserId(userid);
        if (user == null || parseIntSafe(user.get("user_status"), 0) != 1) {
            Map<String, Object> res = new LinkedHashMap<>();
            res.put("result",       "failed");
            res.put("status",       "1004");
            res.put("errormessage", "검색 결과가 없습니다.");
            return res;
        }
        int userLevel = user.getInteger("user_level", 1);
        String token  = jwtUtil.generateToken(userid, userLevel);

        Map<String, Object> userinfo = new LinkedHashMap<>();
        userinfo.put("session",        token);
        userinfo.put("token",          token);
        userinfo.put("userseq",        user.get("user_seq"));
        userinfo.put("userid",         userid);
        userinfo.put("username",       user.getString("user_name"));
        userinfo.put("usercompany",    user.getString("user_company"));
        userinfo.put("useremail",      user.getString("user_email"));
        userinfo.put("userphone",      user.getString("user_phone"));
        userinfo.put("userstatus",     parseIntSafe(user.get("user_status"), 0));
        userinfo.put("userlevel",      userLevel);
        userinfo.put("userpassupdate", user.getString("user_passupdate"));
        userinfo.put("userregdate",    user.getString("user_regdate"));

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("result", "success");
        res.put("status", "200");
        res.put("data",   Map.of("userinfo", userinfo));
        return res;
    }

    // ── 공유 마케터 업데이트 ───────────────────────────────────────────────────

    public Map<String, Object> updateShareManagers(String applyuserid, List<String> shareManagers) {
        shareMongo.updateShareManager(applyuserid, shareManagers != null ? shareManagers : List.of());
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("result", "success");
        res.put("status", "200");
        return res;
    }

    // ── 광고주 등록 ───────────────────────────────────────────────────────────

    public Map<String, Object> registerUser(String userid, String userpass,
                                             String username, String usercompany, String advmarketer) {
        if (userMongo.existsByUserId(userid)) {
            Map<String, Object> res = new LinkedHashMap<>();
            res.put("result",       "failed");
            res.put("status",       "1020");
            res.put("errormessage", "이미 사용중인 아이디입니다.");
            return res;
        }
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        Document doc = new Document()
            .append("user_id",         userid)
            .append("user_pass",       hashSha256(userpass))
            .append("user_name",       username)
            .append("user_company",    usercompany)
            .append("user_email",      "")
            .append("user_phone",      "")
            .append("user_status",     1)
            .append("user_level",      1)
            .append("user_manager",    advmarketer != null ? advmarketer : "")
            .append("user_passupdate", now)
            .append("user_regdate",    now);
        userMongo.insertUser(doc);
        shareMongo.insertShare(userid, advmarketer != null ? advmarketer : "");
        advMongo.insert(userid, "", "", advmarketer != null ? advmarketer : "", now);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("result", "success");
        res.put("status", "200");
        return res;
    }

    // ── 계정 등록 (없을 때만 INSERT) ──────────────────────────────────────────

    public Map<String, Object> registerAccount(String userid, String naverid, String navercustomer,
                                                String naveraccess, String naversecret, String naverdaid,
                                                String kakaosaid, String kakaomomentid, String googleid) {
        if (accountMongo.existsByUserId(userid)) {
            Map<String, Object> res = new LinkedHashMap<>();
            res.put("result",       "success");
            res.put("status",       "0004");
            res.put("errormessage", "중복처리로 적용되지 않았습니다.");
            res.put("resultcount",  0);
            return res;
        }
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("user_id",                userid);
        data.put("account_naver",          naverid      != null ? naverid      : "");
        data.put("account_naver_customer", navercustomer != null ? navercustomer : "");
        data.put("account_naver_access",   naveraccess  != null ? naveraccess  : "");
        data.put("account_naver_secret",   naversecret  != null ? naversecret  : "");
        data.put("account_gfa",            naverdaid    != null ? naverdaid    : "");
        data.put("account_kakaosa",        kakaosaid    != null ? kakaosaid    : "");
        data.put("account_kakaomoment",    kakaomomentid != null ? kakaomomentid : "");
        data.put("account_google",         googleid     != null ? googleid     : "");
        data.put("account_date",           now);
        data.put("account_regdate",        now);
        accountMongo.insert(data);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("result",      "success");
        res.put("status",      "200");
        res.put("resultcount", 1);
        return res;
    }

    private String hashSha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { return input; }
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
