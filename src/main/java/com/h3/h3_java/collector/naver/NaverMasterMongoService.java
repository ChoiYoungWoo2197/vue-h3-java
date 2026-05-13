package com.h3.h3_java.collector.naver;

import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class NaverMasterMongoService {

    private final MongoTemplate mongoTemplate;

    public void upsertCampaign(Map<String, Object> row) {
        upsert("naver_campaign", row, "advkey", "campaignid");
    }

    public void upsertCampaignBudget(Map<String, Object> row) {
        upsert("naver_campaign_budget", row, "advkey", "campaignid");
    }

    public void upsertAdgroup(Map<String, Object> row) {
        upsert("naver_adgroup", row, "advkey", "gid");
    }

    public void upsertAdgroupBudget(Map<String, Object> row) {
        upsert("naver_adgroup_budget", row, "advkey", "gid");
    }

    public void upsertAdExtension(Map<String, Object> row) {
        upsert("naver_adextension", row, "advkey", "extid");
    }

    public void upsertKeywords(List<Map<String, Object>> rows) {
        for (Map<String, Object> row : rows) {
            upsert("naver_keyword", row, "advkey", "kwid");
        }
    }

    public void upsertAd(Map<String, Object> row) {
        upsert("naver_ad", row, "advkey", "adid");
    }

    public void upsertShoppingProduct(Map<String, Object> row) {
        upsert("naver_shopping_product", row, "advkey", "adid");
    }

    public List<Document> selectGroupExtIds(List<String> groupids) {
        Query query = Query.query(Criteria.where("type").is(12).and("ownerid").in(groupids));
        return mongoTemplate.find(query, Document.class, "naver_adextension");
    }

    private void upsert(String collection, Map<String, Object> data, String... keyFields) {
        Criteria criteria = Criteria.where(keyFields[0]).is(data.get(keyFields[0]));
        for (int i = 1; i < keyFields.length; i++) {
            criteria = criteria.and(keyFields[i]).is(data.get(keyFields[i]));
        }
        Query query = Query.query(criteria);
        Update update = new Update();
        data.forEach(update::set);
        mongoTemplate.upsert(query, update, collection);
    }
}