package com.h3.h3_java.raw.mongo;

import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class EmailAuthMongoService {

    private final MongoTemplate mongo;
    private static final String ID_COL = "h3_email_id_log";
    private static final String PW_COL = "h3_email_pw_log";
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // ── 아이디 찾기용 (h3_email_id_log) ───────────────────────────────────────

    public void upsertIdLog(String userName, String userEmail, String code, String ip) {
        Query q = Query.query(
            new Criteria().andOperator(
                Criteria.where("user_name").is(userName),
                Criteria.where("user_email").is(userEmail)
            )
        );
        Update u = new Update()
            .set("user_name",    userName)
            .set("user_email",   userEmail)
            .set("code",         code)
            .set("ip",           ip)
            .set("email_regdate", LocalDateTime.now().format(FMT));
        mongo.upsert(q, u, ID_COL);
    }

    public Document findIdLog(String userName, String userEmail, String code) {
        return mongo.findOne(
            Query.query(new Criteria().andOperator(
                Criteria.where("user_name").is(userName),
                Criteria.where("user_email").is(userEmail),
                Criteria.where("code").is(code)
            )),
            Document.class, ID_COL
        );
    }

    // ── 비밀번호 찾기용 (h3_email_pw_log) ─────────────────────────────────────

    public void upsertPwLog(String userId, String userEmail, String code, String ip) {
        Query q = Query.query(
            new Criteria().andOperator(
                Criteria.where("user_id").is(userId),
                Criteria.where("user_email").is(userEmail)
            )
        );
        Update u = new Update()
            .set("user_id",      userId)
            .set("user_email",   userEmail)
            .set("code",         code)
            .set("ip",           ip)
            .set("email_regdate", LocalDateTime.now().format(FMT));
        mongo.upsert(q, u, PW_COL);
    }

    public Document findPwLog(String userId, String userEmail, String code) {
        return mongo.findOne(
            Query.query(new Criteria().andOperator(
                Criteria.where("user_id").is(userId),
                Criteria.where("user_email").is(userEmail),
                Criteria.where("code").is(code)
            )),
            Document.class, PW_COL
        );
    }
}
