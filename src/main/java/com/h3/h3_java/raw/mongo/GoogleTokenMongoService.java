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

    private final MongoTemplate mongoTemplate;

    private static final String COLLECTION = "google_token";
    private static final String TOKEN_KEY  = "google";

    public Document findToken() {
        return mongoTemplate.findOne(
            Query.query(Criteria.where("key").is(TOKEN_KEY)),
            Document.class, COLLECTION
        );
    }

    public void saveToken(String accessToken, String refreshToken) {
        Query q = Query.query(Criteria.where("key").is(TOKEN_KEY));
        Update u = new Update()
            .set("key",           TOKEN_KEY)
            .set("access_token",  accessToken)
            .set("refresh_token", refreshToken)
            .set("updated_at",    LocalDateTime.now().toString());
        mongoTemplate.upsert(q, u, COLLECTION);
    }
}
