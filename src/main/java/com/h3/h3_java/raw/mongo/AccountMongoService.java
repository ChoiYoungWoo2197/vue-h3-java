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
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AccountMongoService {

    private final MongoTemplate mongo;
    private static final String COL = "h3_account";

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public void insert(Map<String, Object> data) {
        mongo.insert(new Document(data), COL);
    }

    public boolean existsByUserId(String userId) {
        return mongo.exists(Query.query(Criteria.where("user_id").is(userId)), COL);
    }

    public Document findByUserId(String userId) {
        return mongo.findOne(Query.query(Criteria.where("user_id").is(userId)), Document.class, COL);
    }

    public void upsertNaver(String userId, String naverid, String navercustomer,
                            String naveraccess, String naversecret) {
        Update update = new Update()
                .set("account_naver",          naverid)
                .set("account_naver_customer",  navercustomer)
                .set("account_naver_access",    naveraccess)
                .set("account_naver_secret",    naversecret)
                .set("account_date",            now())
                .setOnInsert("user_id",         userId)
                .setOnInsert("account_regdate", now());
        mongo.upsert(Query.query(Criteria.where("user_id").is(userId)), update, COL);
    }

    public void clearNaver(String userId) {
        Update update = new Update()
                .unset("account_naver").unset("account_naver_customer")
                .unset("account_naver_access").unset("account_naver_secret")
                .set("account_date", now());
        mongo.updateFirst(Query.query(Criteria.where("user_id").is(userId)), update, COL);
    }

    public void upsertKakaoSa(String userId, String kakaosaid) {
        Update update = new Update()
                .set("account_kakaosa", kakaosaid)
                .set("account_date",    now())
                .setOnInsert("user_id",         userId)
                .setOnInsert("account_regdate", now());
        mongo.upsert(Query.query(Criteria.where("user_id").is(userId)), update, COL);
    }

    public void clearKakaoSa(String userId) {
        mongo.updateFirst(Query.query(Criteria.where("user_id").is(userId)),
                new Update().unset("account_kakaosa").set("account_date", now()), COL);
    }

    public void upsertNaverDa(String userId, String naverdaid) {
        Update update = new Update()
                .set("account_gfa",  naverdaid)
                .set("account_date", now())
                .setOnInsert("user_id",         userId)
                .setOnInsert("account_regdate", now());
        mongo.upsert(Query.query(Criteria.where("user_id").is(userId)), update, COL);
    }

    public void clearNaverDa(String userId) {
        mongo.updateFirst(Query.query(Criteria.where("user_id").is(userId)),
                new Update().unset("account_gfa").set("account_date", now()), COL);
    }

    public void upsertKakaoMo(String userId, String kakaomomentid) {
        Update update = new Update()
                .set("account_kakaomoment", kakaomomentid)
                .set("account_date",        now())
                .setOnInsert("user_id",         userId)
                .setOnInsert("account_regdate", now());
        mongo.upsert(Query.query(Criteria.where("user_id").is(userId)), update, COL);
    }

    public void clearKakaoMo(String userId) {
        mongo.updateFirst(Query.query(Criteria.where("user_id").is(userId)),
                new Update().unset("account_kakaomoment").set("account_date", now()), COL);
    }

    public void upsertGoogle(String userId, String googleid) {
        Update update = new Update()
                .set("account_google", googleid)
                .set("account_date",   now())
                .setOnInsert("user_id",         userId)
                .setOnInsert("account_regdate", now());
        mongo.upsert(Query.query(Criteria.where("user_id").is(userId)), update, COL);
    }

    public void clearGoogle(String userId) {
        mongo.updateFirst(Query.query(Criteria.where("user_id").is(userId)),
                new Update().unset("account_google").set("account_date", now()), COL);
    }

    public List<Document> findAll() {
        return mongo.findAll(Document.class, COL);
    }

    // ── 매체별 계정 목록 조회 ─────────────────────────────────────────────────

    public List<Document> findNaverAccounts() {
        Query q = Query.query(Criteria.where("account_naver_access").exists(true)
            .and("account_naver_secret").exists(true)
            .and("account_naver_customer").exists(true));
        return mongo.find(q, Document.class, COL);
    }

    public List<Document> findGfaAccounts() {
        Query q = Query.query(Criteria.where("account_gfa").exists(true).ne(null).ne(""));
        return mongo.find(q, Document.class, COL);
    }

    public List<Document> findKakaoSaAccounts() {
        Query q = Query.query(Criteria.where("account_kakaosa").exists(true).ne(null).ne(""));
        return mongo.find(q, Document.class, COL);
    }

    public List<Document> findKakaoMoAccounts() {
        Query q = Query.query(Criteria.where("account_kakaomoment").exists(true).ne(null).ne(""));
        return mongo.find(q, Document.class, COL);
    }

    public List<Document> findGoogleAccounts() {
        Query q = Query.query(Criteria.where("account_google").exists(true).ne(null).ne(""));
        return mongo.find(q, Document.class, COL);
    }

    private String now() {
        return LocalDateTime.now().format(DT_FMT);
    }
}
