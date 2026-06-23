package com.h3.h3_java.raw.mongo;

import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserMongoService {

    private final MongoTemplate mongo;
    private static final String COL = "h3_users";

    public Document findByUserId(String userId) {
        return mongo.findOne(
            Query.query(Criteria.where("user_id").is(userId)),
            Document.class, COL
        );
    }

    public Document findByEmail(String email) {
        return mongo.findOne(
            Query.query(Criteria.where("user_email").is(email)),
            Document.class, COL
        );
    }

    public boolean existsByEmail(String email) {
        return mongo.exists(
            Query.query(Criteria.where("user_email").is(email)),
            COL
        );
    }

    public boolean existsByUserId(String userId) {
        return mongo.exists(
            Query.query(Criteria.where("user_id").is(userId)),
            COL
        );
    }

    public void updateInfo(String userId, String name, String company, String email, String phone) {
        Query q = Query.query(Criteria.where("user_id").is(userId));
        Update u = new Update()
            .set("user_name",    name)
            .set("user_company", company)
            .set("user_email",   email)
            .set("user_phone",   phone);
        mongo.updateFirst(q, u, COL);
    }

    public void updatePassword(String userId, String hashedPass, String passupdate) {
        Query q = Query.query(Criteria.where("user_id").is(userId));
        Update u = new Update()
            .set("user_pass",       hashedPass)
            .set("user_passupdate", passupdate);
        mongo.updateFirst(q, u, COL);
    }

    // ── 에이전트 목록 조회 (user_level 2,3) ───────────────────────────────────

    public List<Document> findAgents(int callerLevel, String callerId,
                                     String field, String searchQuery,
                                     int skip, int limit, boolean desc) {
        Query q = buildAgentQuery(callerLevel, callerId, field, searchQuery);
        q.with(Sort.by(desc ? Sort.Direction.DESC : Sort.Direction.ASC, "user_regdate"));
        q.skip(skip).limit(limit);
        return mongo.find(q, Document.class, COL);
    }

    public long countAgents(int callerLevel, String callerId, String field, String searchQuery) {
        return mongo.count(buildAgentQuery(callerLevel, callerId, field, searchQuery), COL);
    }

    private Query buildAgentQuery(int callerLevel, String callerId,
                                  String field, String searchQuery) {
        Criteria base = new Criteria().orOperator(
            Criteria.where("user_level").is(2),
            Criteria.where("user_level").is(3)
        );
        // PHP auth_check 와 동일: 레벨 2(마케터)는 자기 관리 계정만, 99(관리자)는 전체
        if (callerLevel == 2) {
            base = new Criteria().andOperator(base, Criteria.where("user_manager").is(callerId));
        }
        // 검색 필드
        String mongoField = switch (field != null ? field : "") {
            case "userid"       -> "user_id";
            case "usercompany"  -> "user_company";
            case "usermanager"  -> "user_manager";
            default             -> "user_name";
        };
        if (searchQuery != null && !searchQuery.isBlank()) {
            base = new Criteria().andOperator(base,
                Criteria.where(mongoField).regex(searchQuery, "i"));
        }
        return Query.query(base);
    }

    public void updateUserStatus(String userId, int status) {
        Query q = Query.query(Criteria.where("user_id").is(userId));
        mongo.updateFirst(q, new Update().set("user_status", status), COL);
    }

    public void upsert(Document doc) {
        Query q = Query.query(Criteria.where("user_id").is(doc.getString("user_id")));
        Update u = new Update();
        doc.forEach((k, v) -> { if (v != null) u.setOnInsert(k, v); });
        // 모든 필드를 set으로 저장 (일회성 이관용)
        mongo.upsert(q, Update.fromDocument(doc), COL);
    }

    // ── 마케터 목록 (user_level = 2) ───────────────────────────────────────────

    public List<Document> findMarketers(String field, String query, int skip, int limit, boolean desc) {
        Query q = buildMarketerQuery(field, query);
        q.with(Sort.by(desc ? Sort.Direction.DESC : Sort.Direction.ASC, "user_regdate"));
        q.skip(skip).limit(limit);
        return mongo.find(q, Document.class, COL);
    }

    public long countMarketers(String field, String query) {
        return mongo.count(buildMarketerQuery(field, query), COL);
    }

    private Query buildMarketerQuery(String field, String query) {
        Criteria c = Criteria.where("user_level").is(2);
        if (query != null && !query.isBlank()) {
            String mongoField = "userid".equals(field) ? "user_id" : "user_name";
            c = new Criteria().andOperator(c, Criteria.where(mongoField).regex(query, "i"));
        }
        return Query.query(c);
    }

    // ── 광고주 목록 (user_level = 1) ───────────────────────────────────────────

    public List<Document> findLevel1Users(String field, String query, String manager, int skip, int limit, boolean desc) {
        Query q = buildLevel1Query(field, query, manager);
        q.with(Sort.by(desc ? Sort.Direction.DESC : Sort.Direction.ASC, "user_seq"));
        q.skip(skip).limit(limit);
        return mongo.find(q, Document.class, COL);
    }

    public long countLevel1Users(String field, String query, String manager) {
        return mongo.count(buildLevel1Query(field, query, manager), COL);
    }

    private Query buildLevel1Query(String field, String query, String manager) {
        Criteria c = Criteria.where("user_level").is(1);
        if (manager != null && !manager.isBlank()) {
            c = new Criteria().andOperator(c, Criteria.where("user_manager").regex(manager, "i"));
        }
        if (query != null && !query.isBlank()) {
            String mongoField = "userid".equals(field) ? "user_id" : "user_company";
            c = new Criteria().andOperator(c, Criteria.where(mongoField).regex(query, "i"));
        }
        return Query.query(c);
    }

    public void updateManager(String userId, String managerId) {
        mongo.updateFirst(
            Query.query(Criteria.where("user_id").is(userId)),
            new Update().set("user_manager", managerId),
            COL
        );
    }

    // ── 가입승인 목록 (user_level 0,1) ─────────────────────────────────────────

    public List<Document> findMembershipUsers(String field, String query,
                                              List<String> advUserIds, int skip, int limit, boolean desc) {
        Query q = Query.query(buildMembershipCriteria(field, query, advUserIds));
        q.with(Sort.by(desc ? Sort.Direction.DESC : Sort.Direction.ASC, "user_regdate"));
        q.skip(skip).limit(limit);
        return mongo.find(q, Document.class, COL);
    }

    public long countMembershipUsers(String field, String query, List<String> advUserIds) {
        return mongo.count(Query.query(buildMembershipCriteria(field, query, advUserIds)), COL);
    }

    private Criteria buildMembershipCriteria(String field, String query, List<String> advUserIds) {
        Criteria base = new Criteria().orOperator(
            Criteria.where("user_level").is(0),
            Criteria.where("user_level").is(1)
        );
        if (advUserIds != null) {
            return new Criteria().andOperator(base, Criteria.where("user_id").in(advUserIds));
        }
        if (query != null && !query.isBlank() && field != null && !"advid".equals(field)) {
            String mongoField = switch (field) {
                case "userid"      -> "user_id";
                case "username"    -> "user_name";
                case "usercompany" -> "user_company";
                case "usermanager" -> "user_manager";
                default            -> "user_name";
            };
            return new Criteria().andOperator(base, Criteria.where(mongoField).regex(query, "i"));
        }
        return base;
    }

    // ── 내 광고주 목록 (callerLevel 기반 필터) ───────────────────────────────────

    public List<Document> findMyUsers(int callerLevel, String callerId,
                                      String field, String query, int skip, int limit) {
        Query q = buildMyUsersQuery(callerLevel, callerId, field, query);
        q.with(Sort.by(Sort.Direction.DESC, "user_regdate"));
        q.skip(skip).limit(limit);
        return mongo.find(q, Document.class, COL);
    }

    public long countMyUsers(int callerLevel, String callerId, String field, String query) {
        return mongo.count(buildMyUsersQuery(callerLevel, callerId, field, query), COL);
    }

    private Query buildMyUsersQuery(int callerLevel, String callerId, String field, String query) {
        Criteria c = Criteria.where("user_level").is(1);
        if (callerLevel == 2) {
            c = new Criteria().andOperator(c, Criteria.where("user_manager").is(callerId));
        } else if (callerLevel == 1) {
            c = new Criteria().andOperator(c, Criteria.where("user_id").regex(callerId, "i"));
        }
        if (query != null && !query.isBlank() && field != null) {
            String mf = switch (field) {
                case "userid"      -> "user_id";
                case "usercompany" -> "user_company";
                default            -> "user_name";
            };
            c = new Criteria().andOperator(c, Criteria.where(mf).regex(query, "i"));
        }
        return Query.query(c);
    }

    // ── user_id IN list (공유받은 광고주) ─────────────────────────────────────

    public List<Document> findUsersByIds(List<String> userIds, String field, String query, int skip, int limit) {
        if (userIds == null || userIds.isEmpty()) return List.of();
        Query q = buildUsersByIdsQuery(userIds, field, query);
        q.with(Sort.by(Sort.Direction.DESC, "user_regdate"));
        q.skip(skip).limit(limit);
        return mongo.find(q, Document.class, COL);
    }

    public long countUsersByIds(List<String> userIds, String field, String query) {
        if (userIds == null || userIds.isEmpty()) return 0;
        return mongo.count(buildUsersByIdsQuery(userIds, field, query), COL);
    }

    private Query buildUsersByIdsQuery(List<String> userIds, String field, String query) {
        Criteria c = new Criteria().andOperator(
            Criteria.where("user_level").is(1),
            Criteria.where("user_id").in(userIds)
        );
        if (query != null && !query.isBlank() && field != null) {
            String mf = switch (field) {
                case "userid"      -> "user_id";
                case "usercompany" -> "user_company";
                default            -> "user_name";
            };
            c = new Criteria().andOperator(c, Criteria.where(mf).regex(query, "i"));
        }
        return Query.query(c);
    }

    // ── 광고주 등록 ─────────────────────────────────────────────────────────────

    public boolean existsByUserId(String userId) {
        return mongo.exists(Query.query(Criteria.where("user_id").is(userId)), COL);
    }

    public void insertUser(Document doc) {
        mongo.insert(doc, COL);
    }

    public void updateUserStatusAndLevel(String userId, int status, int level, String managerId) {
        Update u = new Update()
            .set("user_status", status)
            .set("user_level",  level);
        if (managerId != null && !managerId.isBlank()) {
            u.set("user_manager", managerId);
        }
        mongo.updateFirst(Query.query(Criteria.where("user_id").is(userId)), u, COL);
    }
}
