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
            Criteria.where("daily_advid").is(row.get("daily_advid"))
                .and("daily_dt").is(row.get("daily_dt"))
                .and("campaign_id").is(row.get("campaign_id"))
        );
        Update update = new Update();
        row.forEach(update::set);
        mongoTemplate.upsert(query, update, "naver_campaign_daily");
    }

    public boolean hasCampaignDailyData(String customerId, String date) {
        Query query = Query.query(
            Criteria.where("daily_advid").is(customerId).and("daily_dt").is(date)
        );
        return mongoTemplate.exists(query, "naver_campaign_daily");
    }

    public void upsertCampaignHour(Map<String, Object> row) {
        Query query = Query.query(
            Criteria.where("adv_id").is(row.get("adv_id"))
                .and("hour_dt").is(row.get("hour_dt"))
        );
        Update update = new Update();
        row.forEach(update::set);
        mongoTemplate.upsert(query, update, "naver_campaign_hour");
    }

    public boolean hasCampaignHourData(String advId, String date) {
        Query query = Query.query(
            Criteria.where("adv_id").is(advId).and("hour_dt").is(date)
        );
        return mongoTemplate.exists(query, "naver_campaign_hour");
    }

    public List<Document> selectCampaignsByCustomer(String customerId) {
        Query query = Query.query(Criteria.where("advkey").is(customerId));
        return mongoTemplate.find(query, Document.class, "naver_campaign");
    }
}