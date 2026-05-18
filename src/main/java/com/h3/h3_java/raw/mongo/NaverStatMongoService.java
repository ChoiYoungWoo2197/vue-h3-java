package com.h3.h3_java.raw.mongo;

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
public class NaverStatMongoService {

    private final MongoTemplate mongoTemplate;

    public void upsertCampaignDaily(Map<String, Object> row) {
        Query query = Query.query(
            Criteria.where("customerId").is(row.get("customerId"))
                .and("date").is(row.get("date"))
                .and("campaignId").is(row.get("campaignId"))
        );
        Update update = new Update();
        row.forEach(update::set);
        mongoTemplate.upsert(query, update, "naver_campaign_daily");
    }

    public boolean hasCampaignDailyData(String customerId, String date) {
        Query query = Query.query(
            Criteria.where("customerId").is(customerId).and("date").is(date)
        );
        return mongoTemplate.exists(query, "naver_campaign_daily");
    }

    public List<Document> selectCampaignsByCustomer(String customerId) {
        Query query = Query.query(Criteria.where("advkey").is(customerId));
        return mongoTemplate.find(query, Document.class, "naver_campaign");
    }
}