package com.h3.h3_java.raw.mongo;

import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class GoogleTokenMongoService {

    private final MongoTemplate mongo;

    private static final String COL = "google_token";
    private static final String KEY = "google";

    public Document findToken() {
        return mongo.findOne(
            Query.query(Criteria.where("key").is(KEY)),
            Document.class, COL
        );
    }

    // 자동갱신용 (PHP googletoken.php INSERT ON DUPLICATE 와 동일)
    public void saveToken(String accessToken, String refreshToken) {
        Query q = Query.query(Criteria.where("key").is(KEY));
        Update u = new Update()
            .set("key",           KEY)
            .set("access_token",  accessToken)
            .set("refresh_token", refreshToken)
            .set("updated_at",    LocalDateTime.now().toString());
        mongo.upsert(q, u, COL);
    }

    // 최초 인가코드 발급용 (PHP googlesa_access_token.php INSERT 와 동일)
    public void saveFullToken(String code, String accessToken, String refreshToken,
                              Object expiresIn, String idToken, String scope, String tokenType) {
        Query q = Query.query(Criteria.where("key").is(KEY));
        Update u = new Update()
            .set("key",               KEY)
            .set("code",              code)
            .set("access_token",      accessToken)
            .set("refresh_token",     refreshToken)
            .set("access_expires_in", expiresIn)
            .set("id_token",          idToken)
            .set("scope",             scope)
            .set("token_type",        tokenType)
            .set("updated_at",        LocalDateTime.now().toString());
        mongo.upsert(q, u, COL);
    }
}
