package com.h3.h3_java.raw.mongo;

import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 대시보드·분석 서비스용 MongoDB 조회 서비스.
 * 수집 Job과 분리하여 read-only 집계만 담당.
 */
@Service
@RequiredArgsConstructor
public class DashboardMongoService {

    private final MongoTemplate mongo;

    // ─── 기간 합산 (summarymedia, summary) ──────────────────────────────────

    public Map<String, Object> aggregateTotal(String advid, String from, String to, String collection) {
        Aggregation agg = Aggregation.newAggregation(
            Aggregation.match(Criteria.where("daily_advid").is(advid)
                .and("daily_dt").gte(from).lte(to)),
            Aggregation.group()
                .sum("daily_im").as("im")
                .sum("daily_clk").as("clk")
                .sum("daily_cst").as("cst")
                .sum("daily_cv").as("cv")
                .sum("daily_cr").as("cr")
        );
        List<Document> docs = mongo.aggregate(agg, collection, Document.class).getMappedResults();
        if (docs.isEmpty()) return emptyTotals();
        Document d = docs.get(0);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("im",  toDouble(d, "im"));
        r.put("clk", toDouble(d, "clk"));
        r.put("cst", toDouble(d, "cst"));
        r.put("cv",  toDouble(d, "cv"));
        r.put("cr",  toDouble(d, "cr"));
        return r;
    }

    // ─── 날짜별 집계 (period) ────────────────────────────────────────────────

    public Map<String, Map<String, Object>> aggregateByDate(String advid, String from, String to, String collection) {
        Aggregation agg = Aggregation.newAggregation(
            Aggregation.match(Criteria.where("daily_advid").is(advid)
                .and("daily_dt").gte(from).lte(to)),
            Aggregation.group("daily_dt")
                .sum("daily_im").as("im")
                .sum("daily_clk").as("clk")
                .sum("daily_cst").as("cst")
                .sum("daily_cv").as("cv")
                .sum("daily_cr").as("cr"),
            Aggregation.sort(Sort.by("_id"))
        );
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (Document d : mongo.aggregate(agg, collection, Document.class).getMappedResults()) {
            String date = d.getString("_id");
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("im",  toDouble(d, "im"));
            row.put("clk", toDouble(d, "clk"));
            row.put("cst", toDouble(d, "cst"));
            row.put("cv",  toDouble(d, "cv"));
            row.put("cr",  toDouble(d, "cr"));
            result.put(date, row);
        }
        return result;
    }

    // ─── 광고그룹별 집계 (adgroupreport) ────────────────────────────────────

    public List<Map<String, Object>> aggregateByAdgroup(String advid, String from, String to,
                                                         String collection, String advField, String campaignId) {
        Criteria criteria = Criteria.where(advField).is(advid).and("daily_dt").gte(from).lte(to);
        if (campaignId != null && !campaignId.isBlank()) criteria = criteria.and("campaign_id").is(campaignId);

        Aggregation agg = Aggregation.newAggregation(
            Aggregation.match(criteria),
            Aggregation.group(Aggregation.fields().and("adgroup_id","adgroup_id").and("campaign_id","campaign_id"))
                .sum("daily_im").as("im").sum("daily_clk").as("clk").sum("daily_cst").as("cst")
                .sum("daily_cv").as("cv").sum("daily_cr").as("cr")
        );
        List<Map<String, Object>> result = new ArrayList<>();
        for (Document d : mongo.aggregate(agg, collection, Document.class).getMappedResults()) {
            Document id = (Document) d.get("_id");
            if (id == null) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("adgroup_id",  id.getString("adgroup_id"));
            row.put("campaign_id", id.getString("campaign_id"));
            row.put("im",  toDouble(d, "im")); row.put("clk", toDouble(d, "clk"));
            row.put("cst", toDouble(d, "cst")); row.put("cv", toDouble(d, "cv")); row.put("cr", toDouble(d, "cr"));
            result.add(row);
        }
        return result;
    }

    // ─── 캠페인별 집계 (campaignreport) ─────────────────────────────────────

    public List<Map<String, Object>> aggregateByCampaign(String advid, String from, String to, String collection) {
        Aggregation agg = Aggregation.newAggregation(
            Aggregation.match(Criteria.where("daily_advid").is(advid)
                .and("daily_dt").gte(from).lte(to)),
            Aggregation.group("campaign_id")
                .sum("daily_im").as("im")
                .sum("daily_clk").as("clk")
                .sum("daily_cst").as("cst")
                .sum("daily_cv").as("cv")
                .sum("daily_cr").as("cr"),
            Aggregation.sort(Sort.by(Sort.Direction.DESC, "cst"))
        );
        List<Map<String, Object>> result = new ArrayList<>();
        for (Document d : mongo.aggregate(agg, collection, Document.class).getMappedResults()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("campaign_id", d.getString("_id"));
            row.put("im",  toDouble(d, "im"));
            row.put("clk", toDouble(d, "clk"));
            row.put("cst", toDouble(d, "cst"));
            row.put("cv",  toDouble(d, "cv"));
            row.put("cr",  toDouble(d, "cr"));
            result.add(row);
        }
        return result;
    }

    // ─── 전환유형 집계 (Naver SA/GFA only) ──────────────────────────────────

    public Map<String, Map<String, Object>> aggregateConvtype(String advid, String from, String to, String collection) {
        Aggregation agg = Aggregation.newAggregation(
            Aggregation.match(Criteria.where("daily_advid").is(advid)
                .and("daily_dt").gte(from).lte(to)),
            Aggregation.group("conv_type_code")
                .sum("conv_cnt").as("cnt")
                .sum("conv_value").as("value")
        );
        Map<String, Map<String, Object>> result = new HashMap<>();
        for (Document d : mongo.aggregate(agg, collection, Document.class).getMappedResults()) {
            String code = d.getString("_id");
            if (code == null) continue;
            Map<String, Object> v = new HashMap<>();
            v.put("cnt",   toDouble(d, "cnt"));
            v.put("value", toDouble(d, "value"));
            result.put(code, v);
        }
        return result;
    }

    // ─── 전환유형 캠페인별 집계 (campaignreport용) ───────────────────────────

    public Map<String, Map<String, Object>> aggregateConvtypeByCampaignId(String advid, String from, String to, String collection) {
        Aggregation agg = Aggregation.newAggregation(
            Aggregation.match(Criteria.where("daily_advid").is(advid)
                .and("daily_dt").gte(from).lte(to)),
            Aggregation.group(Aggregation.fields().and("campaign_id", "campaign_id").and("conv_type_code", "conv_type_code"))
                .sum("conv_cnt").as("cnt")
                .sum("conv_value").as("value")
        );
        Map<String, Map<String, Object>> result = new HashMap<>();
        for (Document d : mongo.aggregate(agg, collection, Document.class).getMappedResults()) {
            Document id = (Document) d.get("_id");
            if (id == null) continue;
            String cid  = id.getString("campaign_id");
            String code = id.getString("conv_type_code");
            if (cid == null || code == null) continue;
            Map<String, Object> v = new HashMap<>();
            v.put("cnt",   toDouble(d, "cnt"));
            v.put("value", toDouble(d, "value"));
            result.put(cid + "|" + code, v);
        }
        return result;
    }

    // ─── 전환유형 날짜별 집계 (period용) ────────────────────────────────────

    public Map<String, Map<String, Object>> aggregateConvtypeByDate(String advid, String from, String to, String collection) {
        Aggregation agg = Aggregation.newAggregation(
            Aggregation.match(Criteria.where("daily_advid").is(advid)
                .and("daily_dt").gte(from).lte(to)),
            Aggregation.group(Aggregation.fields().and("daily_dt", "daily_dt").and("conv_type_code", "conv_type_code"))
                .sum("conv_cnt").as("cnt")
                .sum("conv_value").as("value"),
            Aggregation.sort(org.springframework.data.domain.Sort.by("_id.daily_dt"))
        );
        // result: Map<"date|typeCode", {cnt, value}>
        Map<String, Map<String, Object>> result = new HashMap<>();
        for (Document d : mongo.aggregate(agg, collection, Document.class).getMappedResults()) {
            Document id = (Document) d.get("_id");
            if (id == null) continue;
            String date = id.getString("daily_dt");
            String code = id.getString("conv_type_code");
            if (date == null || code == null) continue;
            String key = date + "|" + code;
            Map<String, Object> v = new HashMap<>();
            v.put("cnt",   toDouble(d, "cnt"));
            v.put("value", toDouble(d, "value"));
            result.put(key, v);
        }
        return result;
    }

    // ─── 마스터 캠페인 조회 ──────────────────────────────────────────────────

    /** naver_campaign: advkey + campaignid 기준 */
    public List<Document> findNaverCampaigns(String advkey) {
        org.springframework.data.mongodb.core.query.Query q =
            new org.springframework.data.mongodb.core.query.Query(
                Criteria.where("advkey").is(advkey));
        return mongo.find(q, Document.class, "naver_campaign");
    }

    public List<Document> findCampaigns(String advid, String collection) {
        org.springframework.data.mongodb.core.query.Query q =
            new org.springframework.data.mongodb.core.query.Query(
                Criteria.where("advkey").is(advid));
        return mongo.find(q, Document.class, collection);
    }

    // ─── 유틸 ────────────────────────────────────────────────────────────────

    private double toDouble(Document d, String key) {
        Object v = d.get(key);
        if (v == null) return 0.0;
        if (v instanceof Number) return ((Number) v).doubleValue();
        return 0.0;
    }

    private Map<String, Object> emptyTotals() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("im", 0.0); r.put("clk", 0.0); r.put("cst", 0.0);
        r.put("cv", 0.0); r.put("cr", 0.0);
        return r;
    }
}
