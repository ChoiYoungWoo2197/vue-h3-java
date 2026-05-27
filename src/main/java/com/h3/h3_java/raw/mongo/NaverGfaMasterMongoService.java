package com.h3.h3_java.raw.mongo;

import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class NaverGfaMasterMongoService {

    private final MongoTemplate mongoTemplate;

    public void upsertGfaCampaign(Map<String, Object> row) {
        upsert("naver_gfa_campaign", row, "advkey", "cid");
    }

    public void upsertGfaAdgroup(Map<String, Object> row) {
        upsert("naver_gfa_adgroup", row, "advkey", "gid");
    }

    public void upsertGfaAd(Map<String, Object> row) {
        upsert("naver_gfa_ad", row, "advkey", "aid");
    }

    private void upsert(String collection, Map<String, Object> data, String key1, String key2) {
        Criteria criteria = Criteria.where(key1).is(data.get(key1)).and(key2).is(data.get(key2));
        Update update = new Update();
        data.forEach(update::set);
        mongoTemplate.upsert(Query.query(criteria), update, collection);
    }
}
