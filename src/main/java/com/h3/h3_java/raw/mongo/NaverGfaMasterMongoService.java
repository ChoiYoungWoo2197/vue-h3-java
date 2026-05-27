package com.h3.h3_java.raw.mongo;

import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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

    public List<Map<String, Object>> selectGfaCampaigns(String advkey) {
        Query query = Query.query(Criteria.where("advkey").is(advkey));
        query.fields().include("cid").include("cname").include("onoff").exclude("_id");
        return mongoTemplate.find(query, Document.class, "naver_gfa_campaign")
            .stream()
            .map(d -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("cid",   d.getString("cid"));
                m.put("cname", d.getString("cname"));
                m.put("onoff", d.getInteger("onoff", 0));
                return (Map<String, Object>) m;
            })
            .collect(Collectors.toList());
    }

    public Map<String, String> selectGfaAds(String advkey) {
        Query query = Query.query(Criteria.where("advkey").is(advkey));
        query.fields().include("aid").include("gid").exclude("_id");
        return mongoTemplate.find(query, Document.class, "naver_gfa_ad")
            .stream()
            .filter(d -> d.getString("aid") != null)
            .collect(Collectors.toMap(
                d -> d.getString("aid"),
                d -> d.getString("gid") != null ? d.getString("gid") : "",
                (a, b) -> a
            ));
    }

    public Map<String, String> selectGfaAdgroups(String advkey) {
        Query query = Query.query(Criteria.where("advkey").is(advkey));
        query.fields().include("gid").include("cid").exclude("_id");
        return mongoTemplate.find(query, Document.class, "naver_gfa_adgroup")
            .stream()
            .filter(d -> d.getString("gid") != null)
            .collect(Collectors.toMap(
                d -> d.getString("gid"),
                d -> d.getString("cid") != null ? d.getString("cid") : "",
                (a, b) -> a
            ));
    }

    public Set<String> selectGfaCampaignIds(String advkey) {
        Query query = Query.query(Criteria.where("advkey").is(advkey));
        query.fields().include("cid").exclude("_id");
        return mongoTemplate.find(query, Document.class, "naver_gfa_campaign")
            .stream().map(d -> d.getString("cid")).filter(Objects::nonNull)
            .collect(Collectors.toSet());
    }

    // =====================================================================
    // GFA Campaign Daily (MongoDB)
    // =====================================================================

    public void upsertGfaCampaignDaily(Map<String, Object> row) {
        upsert("naver_gfa_campaign_daily", row, "daily_advid", "daily_dt", "campaign_id");
    }

    public boolean hasGfaCampaignDailyData(String advkey, String date) {
        return mongoTemplate.exists(Query.query(
            Criteria.where("daily_advid").is(advkey).and("daily_dt").is(date)
        ), "naver_gfa_campaign_daily");
    }

    // =====================================================================
    // GFA Adgroup Daily (MongoDB)
    // =====================================================================

    public void upsertGfaAdgroupDaily(Map<String, Object> row) {
        upsert("naver_gfa_adgroup_daily", row, "daily_advid", "daily_dt", "adgroup_id");
    }

    public boolean hasGfaAdgroupDailyData(String advkey, String date) {
        return mongoTemplate.exists(Query.query(
            Criteria.where("daily_advid").is(advkey).and("daily_dt").is(date)
        ), "naver_gfa_adgroup_daily");
    }

    // =====================================================================
    // GFA Ad Daily (MongoDB)
    // =====================================================================

    public void upsertGfaAdDaily(Map<String, Object> row) {
        upsert("naver_gfa_ad_daily", row, "daily_advid", "daily_dt", "ad_id");
    }

    public boolean hasGfaAdDailyData(String advkey, String date) {
        return mongoTemplate.exists(Query.query(
            Criteria.where("daily_advid").is(advkey).and("daily_dt").is(date)
        ), "naver_gfa_ad_daily");
    }

    // =====================================================================
    // GFA Budget Alarm 용 집계
    // =====================================================================

    public double sumGfaCampaignDaily(String advkey, String fromDate, String toDate,
                                      String kpiColumn, String campaignId) {
        Criteria criteria = Criteria.where("daily_advid").is(advkey)
            .and("daily_dt").gte(fromDate).lte(toDate);
        if (campaignId != null) {
            criteria = criteria.and("campaign_id").is(campaignId);
        }
        Aggregation agg = Aggregation.newAggregation(
            Aggregation.match(criteria),
            Aggregation.group().sum(kpiColumn).as("total")
        );
        AggregationResults<Document> results =
            mongoTemplate.aggregate(agg, "naver_gfa_campaign_daily", Document.class);
        Document result = results.getUniqueMappedResult();
        if (result == null) return 0.0;
        Object total = result.get("total");
        return total instanceof Number ? ((Number) total).doubleValue() : 0.0;
    }

    // =====================================================================
    // Private Helpers
    // =====================================================================

    private void upsert(String collection, Map<String, Object> data, String... keys) {
        Criteria criteria = Criteria.where(keys[0]).is(data.get(keys[0]));
        for (int i = 1; i < keys.length; i++) {
            criteria = criteria.and(keys[i]).is(data.get(keys[i]));
        }
        Update update = new Update();
        data.forEach(update::set);
        mongoTemplate.upsert(Query.query(criteria), update, collection);
    }
}
