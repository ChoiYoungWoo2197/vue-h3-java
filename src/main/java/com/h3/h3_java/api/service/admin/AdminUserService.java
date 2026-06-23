package com.h3.h3_java.api.service.admin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.h3.h3_java.api.mapper.AdminMapper;
import com.h3.h3_java.raw.mongo.ShareMongoService;
import com.h3.h3_java.raw.mongo.UserMongoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserMongoService  userMongo;
    private final ShareMongoService shareMongo;
    private final AdminMapper       adminMapper;

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
