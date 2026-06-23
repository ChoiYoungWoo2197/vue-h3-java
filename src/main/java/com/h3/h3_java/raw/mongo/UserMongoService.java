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
}
