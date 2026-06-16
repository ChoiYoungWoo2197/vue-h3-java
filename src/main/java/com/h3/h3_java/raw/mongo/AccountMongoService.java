package com.h3.h3_java.raw.mongo;

import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AccountMongoService {

    private final MongoTemplate mongo;
    private static final String COL = "h3_account";

    public void insert(Map<String, Object> data) {
        mongo.insert(new Document(data), COL);
    }

    public boolean existsByUserId(String userId) {
        return mongo.exists(Query.query(Criteria.where("user_id").is(userId)), COL);
    }
}
