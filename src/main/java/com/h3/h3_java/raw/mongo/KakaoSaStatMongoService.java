package com.h3.h3_java.raw.mongo;

import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Kakao SA 통계 데이터 MongoDB 저장 서비스.
 * 중복 체크 기준:
 *   campaign_daily  : advkey + date + campaign_id
 *   campaign_hour   : advkey + date
 *   adgroup_daily   : advkey + date + adgroup_id
 *   ad_daily        : advkey + date + ad_lnk_id
 *   keyword_daily   : advkey + date + keyword_id
 *   budget_alarm    : 별도 insert-only (PHP와 동일하게 중복 체크 후 insert)
 */
@Service
@RequiredArgsConstructor
public class KakaoSaStatMongoService {

    private final MongoTemplate mongoTemplate;

    // ── Campaign Daily ────────────────────────────────────────────────────────

    public boolean existsCampaignDaily(String advkey, String date, String campaignId) {
        Query q = Query.query(
            Criteria.where("advkey").is(advkey)
                .and("daily_dt").is(date)
                .and("campaign_id").is(campaignId)
        );
        return mongoTemplate.exists(q, "kakao_sa_campaign_daily");
    }

    public void insertCampaignDaily(Map<String, Object> doc) {
        mongoTemplate.insert(new Document(doc), "kakao_sa_campaign_daily");
    }

    // ── Campaign Hour ─────────────────────────────────────────────────────────

    public boolean existsCampaignHour(String advkey, String date) {
        Query q = Query.query(
            Criteria.where("advkey").is(advkey)
                .and("hour_dt").is(date)
        );
        return mongoTemplate.exists(q, "kakao_sa_campaign_hour");
    }

    public void insertCampaignHour(Map<String, Object> doc) {
        mongoTemplate.insert(new Document(doc), "kakao_sa_campaign_hour");
    }

    // ── AdGroup Daily ─────────────────────────────────────────────────────────

    public boolean existsAdGroupDaily(String advkey, String date, String adgroupId) {
        Query q = Query.query(
            Criteria.where("advkey").is(advkey)
                .and("daily_dt").is(date)
                .and("adgroup_id").is(adgroupId)
        );
        return mongoTemplate.exists(q, "kakao_sa_adgroup_daily");
    }

    public void insertAdGroupDaily(Map<String, Object> doc) {
        mongoTemplate.insert(new Document(doc), "kakao_sa_adgroup_daily");
    }

    // ── Ad Daily ──────────────────────────────────────────────────────────────

    public boolean existsAdDaily(String advkey, String date, String adLnkId) {
        Query q = Query.query(
            Criteria.where("advkey").is(advkey)
                .and("daily_dt").is(date)
                .and("ad_id").is(adLnkId)
        );
        return mongoTemplate.exists(q, "kakao_sa_ad_daily");
    }

    public void insertAdDaily(Map<String, Object> doc) {
        mongoTemplate.insert(new Document(doc), "kakao_sa_ad_daily");
    }

    // ── Keyword Daily ─────────────────────────────────────────────────────────

    public boolean existsKeywordDaily(String advkey, String date, String keywordId) {
        Query q = Query.query(
            Criteria.where("advkey").is(advkey)
                .and("daily_dt").is(date)
                .and("keyword_id").is(keywordId)
        );
        return mongoTemplate.exists(q, "kakao_sa_keyword_daily");
    }

    public void insertKeywordDaily(Map<String, Object> doc) {
        mongoTemplate.insert(new Document(doc), "kakao_sa_keyword_daily");
    }

    // ── Budget Alarm ──────────────────────────────────────────────────────────

    public long countBudgetAlarm(Query q) {
        return mongoTemplate.count(q, "kakao_sa_budget_alarm");
    }

    public void insertBudgetAlarm(Map<String, Object> doc) {
        mongoTemplate.insert(new Document(doc), "kakao_sa_budget_alarm");
    }

    // ── Campaign Daily 집계 (알람용) ──────────────────────────────────────────

    public long sumCampaignDailyStat(String advkey, String campaignId, String field,
                                      String fromDate, String toDate) {
        Query q = Query.query(
            Criteria.where("advkey").is(advkey)
                .and("campaign_id").is(campaignId)
                .and("daily_dt").gte(fromDate).lte(toDate)
        );
        return mongoTemplate.find(q, Document.class, "kakao_sa_campaign_daily")
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
        return mongoTemplate.find(q, Document.class, "kakao_sa_campaign_daily")
            .stream()
            .mapToLong(d -> {
                Object v = d.get(field);
                return v instanceof Number ? ((Number) v).longValue() : 0L;
            })
            .sum();
    }
}
