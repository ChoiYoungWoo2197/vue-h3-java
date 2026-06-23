package com.h3.h3_java.raw.mongo;

import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdvMongoService {

    private final MongoTemplate mongo;
    private static final String COL = "h3_adv";

    public Document findByUserId(String userId) {
        return mongo.findOne(Query.query(Criteria.where("user_id").is(userId)), Document.class, COL);
    }

    public List<Document> findByUserIds(List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) return List.of();
        return mongo.find(Query.query(Criteria.where("user_id").in(userIds)), Document.class, COL);
    }

    public void upsert(String userId, String advId, String advMedia,
                       String advRegdate, String advMarketer, String advMarketerRegdate, int advSalseDay) {
        Query q = Query.query(Criteria.where("user_id").is(userId));
        Update u = new Update()
            .set("user_id",              userId)
            .set("adv_id",               advId)
            .set("adv_media",            advMedia)
            .set("adv_regdate",          advRegdate)
            .set("adv_marketer",         advMarketer)
            .set("adv_marketer_regdate", advMarketerRegdate)
            .set("adv_salse_day",        advSalseDay);
        mongo.upsert(q, u, COL);
    }

    public void insert(String userId, String advId, String advMedia,
                       String advMarketer, String regdate) {
        Document doc = new Document()
            .append("user_id",              userId)
            .append("adv_id",               advId)
            .append("adv_media",            advMedia)
            .append("adv_regdate",          regdate)
            .append("adv_marketer",         advMarketer)
            .append("adv_marketer_regdate", regdate)
            .append("adv_salse_day",        0);
        mongo.insert(doc, COL);
    }
}
