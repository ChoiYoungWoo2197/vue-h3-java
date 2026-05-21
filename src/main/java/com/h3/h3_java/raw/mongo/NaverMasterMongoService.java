package com.h3.h3_java.raw.mongo;

import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

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
        if (rows.isEmpty()) return;
        BulkOperations bulk = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, "naver_keyword");
        for (Map<String, Object> row : rows) {
            Criteria criteria = Criteria.where("advkey").is(row.get("advkey"))
                                        .and("kwid").is(row.get("kwid"));
            Update update = new Update();
            row.forEach(update::set);
            bulk.upsert(Query.query(criteria), update);
        }
        bulk.execute();
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

    public List<String> selectKeywordIds(String advkey) {
        Query query = Query.query(Criteria.where("advkey").is(advkey));
        query.fields().include("kwid").exclude("_id");
        return mongoTemplate.find(query, Document.class, "naver_keyword")
            .stream().map(d -> d.getString("kwid")).filter(Objects::nonNull).collect(Collectors.toList());
    }

    public void bulkUpdateKeywordDetails(String advkey, Map<String, Map<String, Object>> kwUpdateMap) {
        if (kwUpdateMap.isEmpty()) return;
        BulkOperations bulk = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, "naver_keyword");
        for (Map.Entry<String, Map<String, Object>> entry : kwUpdateMap.entrySet()) {
            Query query = Query.query(Criteria.where("advkey").is(advkey).and("kwid").is(entry.getKey()));
            Update update = new Update();
            entry.getValue().forEach(update::set);
            bulk.updateOne(query, update);
        }
        bulk.execute();
    }

    public List<Document> selectAdDocs(String advkey) {
        Query query = Query.query(Criteria.where("advkey").is(advkey));
        query.fields().include("adid").include("groupid").exclude("_id");
        return mongoTemplate.find(query, Document.class, "naver_ad");
    }

    public List<String> selectShoppingProductIds(String advkey) {
        Query query = Query.query(Criteria.where("advkey").is(advkey));
        query.fields().include("adid").exclude("_id");
        return mongoTemplate.find(query, Document.class, "naver_shopping_product")
            .stream().map(d -> d.getString("adid")).filter(Objects::nonNull).collect(Collectors.toList());
    }

    public void updateAdDetail(String advkey, String adid, Map<String, Object> update) {
        Query query = Query.query(Criteria.where("advkey").is(advkey).and("adid").is(adid));
        Update u = new Update();
        update.forEach(u::set);
        mongoTemplate.updateFirst(query, u, "naver_ad");
    }

    public void updateShoppingProductDetail(String advkey, String adid, Map<String, Object> update) {
        Query query = Query.query(Criteria.where("advkey").is(advkey).and("adid").is(adid));
        Update u = new Update();
        update.forEach(u::set);
        mongoTemplate.updateFirst(query, u, "naver_shopping_product");
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