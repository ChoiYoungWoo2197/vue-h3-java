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
public class KakaoMoStatMongoService {

    private final MongoTemplate mongoTemplate;

    // ── Campaign Daily ────────────────────────────────────────────────────────

    public boolean existsCampaignDaily(String advkey, String date, String campaignId) {
        Query q = Query.query(
            Criteria.where("advkey").is(advkey)
                .and("daily_dt").is(date)
                .and("campaign_id").is(campaignId)
        );
        return mongoTemplate.exists(q, "kakao_mo_campaign_daily");
    }

    public void insertCampaignDaily(Map<String, Object> doc) {
        mongoTemplate.insert(new Document(doc), "kakao_mo_campaign_daily");
    }

    // ── Campaign Hour ─────────────────────────────────────────────────────────

    public boolean existsCampaignHour(String advkey, String date) {
        Query q = Query.query(
            Criteria.where("advkey").is(advkey)
                .and("hour_dt").is(date)
        );
        return mongoTemplate.exists(q, "kakao_mo_campaign_hour");
    }

    public void insertCampaignHour(Map<String, Object> doc) {
        mongoTemplate.insert(new Document(doc), "kakao_mo_campaign_hour");
    }

    // ── AdGroup Daily ─────────────────────────────────────────────────────────

    public boolean existsAdGroupDaily(String advkey, String date, String adgroupId) {
        Query q = Query.query(
            Criteria.where("advkey").is(advkey)
                .and("daily_dt").is(date)
                .and("adgroup_id").is(adgroupId)
        );
        return mongoTemplate.exists(q, "kakao_mo_adgroup_daily");
    }

    public void insertAdGroupDaily(Map<String, Object> doc) {
        mongoTemplate.insert(new Document(doc), "kakao_mo_adgroup_daily");
    }

    // ── Ad Daily ──────────────────────────────────────────────────────────────

    public boolean existsAdDaily(String advkey, String date, String adId) {
        Query q = Query.query(
            Criteria.where("advkey").is(advkey)
                .and("daily_dt").is(date)
                .and("ad_id").is(adId)
        );
        return mongoTemplate.exists(q, "kakao_mo_ad_daily");
    }

    public void insertAdDaily(Map<String, Object> doc) {
        mongoTemplate.insert(new Document(doc), "kakao_mo_ad_daily");
    }

    // ── Budget Alarm ──────────────────────────────────────────────────────────

    public long countBudgetAlarm(Query q) {
        return mongoTemplate.count(q, "kakao_mo_budget_alarm");
    }

    public void insertBudgetAlarm(Map<String, Object> doc) {
        mongoTemplate.insert(new Document(doc), "kakao_mo_budget_alarm");
    }

    // ── Campaign Daily 집계 (알람용) ──────────────────────────────────────────

    public long sumCampaignDailyStat(String advkey, String campaignId, String field,
                                      String fromDate, String toDate) {
        Query q = Query.query(
            Criteria.where("advkey").is(advkey)
                .and("campaign_id").is(campaignId)
                .and("daily_dt").gte(fromDate).lte(toDate)
        );
        return mongoTemplate.find(q, Document.class, "kakao_mo_campaign_daily")
            .stream()
            .mapToLong(d -> {
                Object v = d.get(field);
                return v instanceof Number ? ((Number) v).longValue() : 0L;
            })
            .sum();
    }

    public long sumAccountDailyStat(String advkey, String field,
                                     String fromDate, String toDate) {
        Query q = Query.query(
            Criteria.where("advkey").is(advkey)
                .and("daily_dt").gte(fromDate).lte(toDate)
        );
        return mongoTemplate.find(q, Document.class, "kakao_mo_campaign_daily")
            .stream()
            .mapToLong(d -> {
                Object v = d.get(field);
                return v instanceof Number ? ((Number) v).longValue() : 0L;
            })
            .sum();
    }
}
