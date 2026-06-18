package com.h3.h3_java.raw.mongo;

import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

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

    public void upsert(Document doc) {
        Query q = Query.query(Criteria.where("user_id").is(doc.getString("user_id")));
        Update u = new Update();
        doc.forEach((k, v) -> { if (v != null) u.setOnInsert(k, v); });
        // 모든 필드를 set으로 저장 (일회성 이관용)
        mongo.upsert(q, Update.fromDocument(doc), COL);
    }
}
