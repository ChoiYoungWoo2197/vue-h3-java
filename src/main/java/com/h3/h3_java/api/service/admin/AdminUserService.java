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

    // ── 에이전시 광고주 목록 (내 광고주 + 일별 비용) ───────────────────────────

    private static final Map<String, String> COST_TABLES = Map.of(
        "N",      "h3_campaign_daily_naver",
        "NDA",    "h3_campaign_daily_navergfa",
        "D",      "h3_campaign_daily_kakaokeyword",
        "K",      "h3_campaign_daily_kakaomoment",
        "google", "h3_campaign_daily_google"
    );

    private Map<String, Long> buildCostMap(String tableKey, String fromDate, String toDate) {
        String table = COST_TABLES.get(tableKey);
        if (table == null || fromDate == null || toDate == null) return Map.of();
        List<Map<String, Object>> rows = adminMapper.selectDailyCstByTable(table, fromDate, toDate);
        Map<String, Long> map = new HashMap<>();
        for (Map<String, Object> row : rows) {
            String advId = objStr(row.get("daily_advid"));
            if (advId != null) map.put(advId, parseIntSafe(row.get("cst"), 0));
        }
        return map;
    }

    private String toSortField(String sort) {
        return switch (sort != null ? sort : "nd") {
            case "nd", "na"           -> "naver";
            case "ndad", "ndaa"       -> "naverda";
            case "ksd", "ksa"         -> "kakaosa";
            case "kmd", "kma"         -> "kakaomo";
            case "googled", "googlea" -> "google";
            default                   -> "naver";
        };
    }

    private List<Map<String, Object>> buildAgencyUserList(List<Document> allDocs,
                                                           Map<String, Long> naverMap,
                                                           Map<String, Long> naverDaMap,
                                                           Map<String, Long> kakaoSaMap,
                                                           Map<String, Long> kakaoMoMap,
                                                           Map<String, Long> googleMap,
                                                           Map<String, Document> accountMap) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Document d : allDocs) {
            String userId = d.getString("user_id");
            Document acct = accountMap.get(userId);
            long naver   = acct != null ? naverMap.getOrDefault(  nvl(acct.getString("account_naver_customer")), 0L) : 0L;
            long naverda = acct != null ? naverDaMap.getOrDefault(nvl(acct.getString("account_gfa")),            0L) : 0L;
            long kakaosa = acct != null ? kakaoSaMap.getOrDefault(nvl(acct.getString("account_kakaosa")),        0L) : 0L;
            long kakaomo = acct != null ? kakaoMoMap.getOrDefault(nvl(acct.getString("account_kakaomoment")),    0L) : 0L;
            long google  = acct != null ? googleMap.getOrDefault( nvl(acct.getString("account_google")),         0L) : 0L;
            Map<String, Object> u = new LinkedHashMap<>();
            u.put("userid",      userId);
            u.put("usercompany", d.getString("user_company"));
            u.put("username",    d.getString("user_name"));
            u.put("naver",       naver);
            u.put("naverda",     naverda);
            u.put("kakaosa",     kakaosa);
            u.put("kakaomo",     kakaomo);
            u.put("google",      google);
            list.add(u);
        }
        return list;
    }

    private String nvl(String s) { return s != null ? s : ""; }

    public Map<String, Object> getAgencyUsers(String callerId, int callerLevel,
                                               String query, String field, String sort,
                                               int start, int display,
                                               String fromDate, String toDate) {
        List<Document> allDocs = userMongo.findMyUsers(callerLevel, callerId, field, query, 0, 10000);
        long total = allDocs.size();

        List<String> allIds = allDocs.stream()
            .map(d -> d.getString("user_id")).filter(Objects::nonNull).collect(Collectors.toList());
        Map<String, Document> accountMap = accountMongo.findByUserIds(allIds).stream()
            .collect(Collectors.toMap(d -> d.getString("user_id"), d -> d, (a, b) -> a));

        Map<String, Long> naverMap   = buildCostMap("N",      fromDate, toDate);
        Map<String, Long> naverDaMap = buildCostMap("NDA",    fromDate, toDate);
        Map<String, Long> kakaoSaMap = buildCostMap("D",      fromDate, toDate);
        Map<String, Long> kakaoMoMap = buildCostMap("K",      fromDate, toDate);
        Map<String, Long> googleMap  = buildCostMap("google", fromDate, toDate);

        List<Map<String, Object>> all = buildAgencyUserList(
            allDocs, naverMap, naverDaMap, kakaoSaMap, kakaoMoMap, googleMap, accountMap);

        String sortField = toSortField(sort);
        boolean desc = sort == null || !sort.endsWith("a");
        all.sort(Comparator.comparingLong((Map<String, Object> m) ->
            ((Number) m.get(sortField)).longValue()).reversed());
        if (!desc) all.sort(Comparator.comparingLong(m -> ((Number) m.get(sortField)).longValue()));

        List<Map<String, Object>> page = all.stream()
            .skip((long) start * display).limit(display).collect(Collectors.toList());

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("result",      "success");
        res.put("status",      "200");
        res.put("totalcount",  total);
        res.put("resultcount", page.size());
        res.put("data",        Map.of("users", page));
        return res;
    }

    public Map<String, Object> getAgencyBeSharedUsers(String callerId, int callerLevel,
                                                        String query, String field, String sort,
                                                        int start, int display,
                                                        String fromDate, String toDate) {
        List<String> sharedIds = shareMongo.findUserIdsByShareManager(callerId);
        if (sharedIds.isEmpty()) {
            Map<String, Object> res = new LinkedHashMap<>();
            res.put("result", "success"); res.put("status", "200");
            res.put("totalcount", 0L); res.put("resultcount", 0);
            res.put("data", Map.of("users", List.of()));
            return res;
        }

        List<Document> allDocs = userMongo.findUsersByIds(sharedIds, field, query, 0, 10000);
        long total = allDocs.size();

        List<String> allIds = allDocs.stream()
            .map(d -> d.getString("user_id")).filter(Objects::nonNull).collect(Collectors.toList());
        Map<String, Document> accountMap = accountMongo.findByUserIds(allIds).stream()
            .collect(Collectors.toMap(d -> d.getString("user_id"), d -> d, (a, b) -> a));

        Map<String, Long> naverMap   = buildCostMap("N",      fromDate, toDate);
        Map<String, Long> naverDaMap = buildCostMap("NDA",    fromDate, toDate);
        Map<String, Long> kakaoSaMap = buildCostMap("D",      fromDate, toDate);
        Map<String, Long> kakaoMoMap = buildCostMap("K",      fromDate, toDate);
        Map<String, Long> googleMap  = buildCostMap("google", fromDate, toDate);

        List<Map<String, Object>> all = buildAgencyUserList(
            allDocs, naverMap, naverDaMap, kakaoSaMap, kakaoMoMap, googleMap, accountMap);

        String sortField = toSortField(sort);
        boolean desc = sort == null || !sort.endsWith("a");
        all.sort(Comparator.comparingLong((Map<String, Object> m) ->
            ((Number) m.get(sortField)).longValue()).reversed());
        if (!desc) all.sort(Comparator.comparingLong(m -> ((Number) m.get(sortField)).longValue()));

        List<Map<String, Object>> page = all.stream()
            .skip((long) start * display).limit(display).collect(Collectors.toList());

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("result",      "success");
        res.put("status",      "200");
        res.put("totalcount",  total);
        res.put("resultcount", page.size());
        res.put("data",        Map.of("users", page));
        return res;
    }

    // ── 통합 알림 ─────────────────────────────────────────────────────────────

    private static final Map<String, String> MEDIA_SET = Map.of(
        "naver",   "네이버검색광고",
        "kakaosa", "카카오검색광고",
        "naverda", "네이버디스플레이",
        "kakaomo", "카카오모먼트"
    );
    private static final Map<String, String> TYPE_SET = Map.of(
        "bizmoney_locked_by_budget",        "계정 비즈머니 중단",
        "campaign_limited_by_budget",       "캠페인 하루예산 중단",
        "adgroup_limited_by_budget",        "광고그룹 하루예산 중단",
        "account_rate_updown_by_clk",       "계정 클릭수 증감률",
        "account_rate_updown_by_im",        "계정 노출수 증감률",
        "account_rate_updown_by_cv",        "계정 전환수 증감률",
        "account_rate_updown_by_cr",        "계정 전환매출 증감률",
        "campaign_rate_updown_by_clk",      "캠페인 클릭수 증감률",
        "campaign_rate_updown_by_im",       "캠페인 노출수 증감률"
    );

    public Map<String, Object> getAgencyAlarms(String callerId, int callerLevel,
                                                int start, int display) {
        List<Map<String, Object>> rows;
        long total;

        if (callerLevel == 99) {
            // admin: 전체 알림
            rows  = adminMapper.selectAlarmForAdmin(start * display, display);
            total = adminMapper.countAlarmForAdmin();

            // MongoDB에서 manager 정보 조회 (share → users)
            List<String> userIds = rows.stream()
                .map(r -> objStr(r.get("user_id"))).filter(Objects::nonNull).distinct().collect(Collectors.toList());
            Map<String, Document> shareMap = shareMongo.findByUserIds(userIds).stream()
                .collect(Collectors.toMap(d -> d.getString("user_id"), d -> d, (a, b) -> a));
            Map<String, String> managerNameCache = new HashMap<>();

            List<Map<String, Object>> alarms = new ArrayList<>();
            for (Map<String, Object> r : rows) {
                String userId    = objStr(r.get("user_id"));
                Document share   = shareMap.get(userId);
                String managerId = share != null ? share.getString("user_manager") : null;
                String mName     = "";
                if (managerId != null && !managerId.isBlank()) {
                    mName = managerNameCache.computeIfAbsent(managerId, id -> {
                        Document u = userMongo.findByUserId(id);
                        return u != null && u.getString("user_name") != null ? u.getString("user_name") : id;
                    });
                }
                alarms.add(buildAlarmRow(r, managerId, mName));
            }
            return alarmResponse(alarms, total);

        } else {
            // marketer: adv_marketer 기준으로 advIds 조회
            List<Document> advDocs = advMongo.findByMarketer(callerId);
            if (advDocs.isEmpty()) return alarmResponse(List.of(), 0L);

            List<String> userIds = advDocs.stream()
                .map(d -> d.getString("user_id")).filter(Objects::nonNull).collect(Collectors.toList());
            List<Document> accounts = accountMongo.findByUserIds(userIds);

            List<String> advIds = new ArrayList<>();
            String[] fields = {"account_naver_customer", "account_kakaosa", "account_gfa", "account_kakaomoment"};
            for (Document a : accounts) {
                for (String f : fields) {
                    String v = a.getString(f);
                    if (v != null && !v.isBlank()) advIds.add(v);
                }
            }
            if (advIds.isEmpty()) return alarmResponse(List.of(), 0L);

            rows  = adminMapper.selectAlarmForMarketer(advIds, start * display, display);
            total = adminMapper.countAlarmForMarketer(advIds);

            List<Map<String, Object>> alarms = new ArrayList<>();
            for (Map<String, Object> r : rows) {
                alarms.add(buildAlarmRow(r, null, null));
            }
            return alarmResponse(alarms, total);
        }
    }

    private Map<String, Object> buildAlarmRow(Map<String, Object> r,
                                               String managerId, String managerName) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("user_id",       objStr(r.get("user_id")));
        m.put("daily_advid",   objStr(r.get("daily_advid")));
        m.put("daily_dt",      objStr(r.get("daily_dt")));
        String media = objStr(r.get("media"));
        m.put("media",         media != null ? MEDIA_SET.getOrDefault(media, media) : "");
        m.put("target_id",     objStr(r.get("target_id")));
        m.put("target_name",   objStr(r.get("target_name")));
        m.put("level",         objStr(r.get("level")));
        String type = objStr(r.get("type"));
        m.put("type",          type != null ? TYPE_SET.getOrDefault(type, type) : "");
        m.put("content",       objStr(r.get("content")));
        m.put("daily_regdate", objStr(r.get("daily_regdate")));
        m.put("manager_id",    managerId);
        m.put("manager_name",  managerName);
        return m;
    }

    private Map<String, Object> alarmResponse(List<Map<String, Object>> alarms, long total) {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("result",      "success");
        res.put("status",      "200");
        res.put("totalcount",  total);
        res.put("resultcount", alarms.size());
        res.put("data",        Map.of("alarms", alarms));
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
