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
public class GoogleStatMongoService {

    private final MongoTemplate mongoTemplate;

    // ── Campaign Daily ────────────────────────────────────────────────────────

    public boolean hasCampaignDaily(String advkey, String date) {
        return exists("google_campaign_daily", "daily_advid", advkey, "daily_dt", date);
    }

    public void upsertCampaignDaily(Map<String, Object> doc) {
        upsert("google_campaign_daily", doc, "daily_advid", "daily_dt", "campaign_id");
    }

    // ── Campaign Hour ─────────────────────────────────────────────────────────

    public boolean hasCampaignHour(String advkey, String date) {
        return exists("google_campaign_hour", "adv_id", advkey, "hour_dt", date);
    }

    public void upsertCampaignHour(Map<String, Object> doc) {
        upsert("google_campaign_hour", doc, "adv_id", "hour_dt");
    }

    // ── AdGroup Daily ─────────────────────────────────────────────────────────

    public boolean hasAdGroupDaily(String advkey, String date) {
        return exists("google_adgroup_daily", "daily_advid", advkey, "daily_dt", date);
    }

    public void upsertAdGroupDaily(Map<String, Object> doc) {
        upsert("google_adgroup_daily", doc, "daily_advid", "daily_dt", "adgroup_id");
    }

    // ── Ad Daily ──────────────────────────────────────────────────────────────

    public boolean hasAdDaily(String advkey, String date) {
        return exists("google_ad_daily", "daily_advid", advkey, "daily_dt", date);
    }

    public void upsertAdDaily(Map<String, Object> doc) {
        upsert("google_ad_daily", doc, "daily_advid", "daily_dt", "ad_id");
    }

    // ── Keyword Daily ─────────────────────────────────────────────────────────

    public boolean hasKeywordDaily(String advkey, String date) {
        return exists("google_keyword_daily", "daily_advid", advkey, "daily_dt", date);
    }

    public void upsertKeywordDaily(Map<String, Object> doc) {
        upsert("google_keyword_daily", doc, "daily_advid", "daily_dt", "keyword_id");
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private boolean exists(String collection, String field1, Object val1, String field2, Object val2) {
        Query q = Query.query(Criteria.where(field1).is(val1).and(field2).is(val2));
        return mongoTemplate.exists(q, collection);
    }

    private void upsert(String collection, Map<String, Object> doc, String... idFields) {
        Criteria criteria = Criteria.where(idFields[0]).is(doc.get(idFields[0]));
        for (int i = 1; i < idFields.length; i++) {
            criteria = criteria.and(idFields[i]).is(doc.get(idFields[i]));
        }
        Query q = Query.query(criteria);
        Update u = new Update();
        doc.forEach(u::set);
        mongoTemplate.upsert(q, u, collection);
    }
}
