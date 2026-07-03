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
        String advField = getAdvField(collection);
        Aggregation agg = Aggregation.newAggregation(
            Aggregation.match(Criteria.where(advField).is(advid)
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
        String advField = getAdvField(collection);
        Aggregation agg = Aggregation.newAggregation(
            Aggregation.match(Criteria.where(advField).is(advid)
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

    // ─── 전환유형 소재별 집계 (adreport용) ───────────────────────────────────

    public Map<String, Map<String, Object>> aggregateConvtypeByAdId(String advid, String from, String to, String collection) {
        Aggregation agg = Aggregation.newAggregation(
            Aggregation.match(Criteria.where("daily_advid").is(advid).and("daily_dt").gte(from).lte(to)),
            Aggregation.group(Aggregation.fields().and("ad_id","ad_id").and("conv_type_code","conv_type_code"))
                .sum("conv_cnt").as("cnt").sum("conv_value").as("value")
        );
        Map<String, Map<String, Object>> result = new HashMap<>();
        for (Document d : mongo.aggregate(agg, collection, Document.class).getMappedResults()) {
            Document id = (Document) d.get("_id");
            if (id == null) continue;
            String aid  = id.getString("ad_id");
            String code = id.getString("conv_type_code");
            if (aid == null || code == null) continue;
            Map<String, Object> v = new HashMap<>();
            v.put("cnt",   toDouble(d, "cnt"));
            v.put("value", toDouble(d, "value"));
            result.put(aid + "|" + code, v);
        }
        return result;
    }

    // ─── 소재별 집계 (adreport용) ────────────────────────────────────────────

    public List<Map<String, Object>> aggregateByAd(String advid, String from, String to,
                                                     String collection, String advField) {
        Aggregation agg = Aggregation.newAggregation(
            Aggregation.match(Criteria.where(advField).is(advid).and("daily_dt").gte(from).lte(to)),
            Aggregation.group("ad_id")
                .first("campaign_id").as("campaign_id")
                .first("adgroup_id").as("adgroup_id")
                .sum("daily_im").as("im").sum("daily_clk").as("clk").sum("daily_cst").as("cst")
                .sum("daily_cv").as("cv").sum("daily_cr").as("cr")
        );
        List<Map<String, Object>> result = new ArrayList<>();
        for (Document d : mongo.aggregate(agg, collection, Document.class).getMappedResults()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("ad_id",       d.getString("_id"));
            row.put("campaign_id", d.getString("campaign_id"));
            row.put("adgroup_id",  d.getString("adgroup_id"));
            row.put("im",  toDouble(d,"im")); row.put("clk", toDouble(d,"clk"));
            row.put("cst", toDouble(d,"cst")); row.put("cv", toDouble(d,"cv")); row.put("cr", toDouble(d,"cr"));
            result.add(row);
        }
        return result;
    }

    // ─── 소재별 집계 (필터 포함: adgroupad / campaignad) ─────────────────────

    public List<Map<String, Object>> aggregateByAdWithFilter(String advid, String from, String to,
                                                              String collection, String advField,
                                                              String filterField, String filterValue) {
        Criteria criteria = Criteria.where(advField).is(advid).and("daily_dt").gte(from).lte(to);
        if (filterField != null && !filterField.isBlank() && filterValue != null && !filterValue.isBlank()) {
            criteria = criteria.and(filterField).is(filterValue);
        }
        Aggregation agg = Aggregation.newAggregation(
            Aggregation.match(criteria),
            Aggregation.group("ad_id")
                .first("campaign_id").as("campaign_id")
                .first("adgroup_id").as("adgroup_id")
                .sum("daily_im").as("im").sum("daily_clk").as("clk").sum("daily_cst").as("cst")
                .sum("daily_cv").as("cv").sum("daily_cr").as("cr")
        );
        List<Map<String, Object>> result = new ArrayList<>();
        for (Document d : mongo.aggregate(agg, collection, Document.class).getMappedResults()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("ad_id",       d.getString("_id"));
            row.put("campaign_id", d.getString("campaign_id"));
            row.put("adgroup_id",  d.getString("adgroup_id"));
            row.put("im",  toDouble(d,"im")); row.put("clk", toDouble(d,"clk"));
            row.put("cst", toDouble(d,"cst")); row.put("cv", toDouble(d,"cv")); row.put("cr", toDouble(d,"cr"));
            result.add(row);
        }
        return result;
    }

    // ─── 전환유형 키워드별 집계 (keywordreport용) ─────────────────────────────

    public Map<String, Map<String, Object>> aggregateConvtypeByKeywordId(String advid, String from, String to, String collection) {
        Aggregation agg = Aggregation.newAggregation(
            Aggregation.match(Criteria.where("daily_advid").is(advid).and("daily_dt").gte(from).lte(to)),
            Aggregation.group(Aggregation.fields().and("keyword_id","keyword_id").and("conv_type_code","conv_type_code"))
                .sum("conv_cnt").as("cnt").sum("conv_value").as("value")
        );
        Map<String, Map<String, Object>> result = new HashMap<>();
        for (Document d : mongo.aggregate(agg, collection, Document.class).getMappedResults()) {
            Document id = (Document) d.get("_id");
            if (id == null) continue;
            String kwid = id.getString("keyword_id");
            String code = id.getString("conv_type_code");
            if (kwid == null || code == null) continue;
            Map<String, Object> v = new HashMap<>();
            v.put("cnt",   toDouble(d, "cnt"));
            v.put("value", toDouble(d, "value"));
            result.put(kwid + "|" + code, v);
        }
        return result;
    }

    // ─── 키워드별 집계 (keywordreport) ──────────────────────────────────────

    public List<Map<String, Object>> aggregateByKeyword(String advid, String from, String to,
                                                         String collection, String advField) {
        Aggregation agg = Aggregation.newAggregation(
            Aggregation.match(Criteria.where(advField).is(advid).and("daily_dt").gte(from).lte(to)),
            Aggregation.group("keyword_id")
                .first("adgroup_id").as("adgroup_id")
                .first("campaign_id").as("campaign_id")
                .first("kname").as("kname")
                .sum("daily_im").as("im").sum("daily_clk").as("clk").sum("daily_cst").as("cst")
                .sum("daily_cv").as("cv").sum("daily_cr").as("cr")
        );
        List<Map<String, Object>> result = new ArrayList<>();
        for (Document d : mongo.aggregate(agg, collection, Document.class).getMappedResults()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("keyword_id",  d.getString("_id"));
            row.put("adgroup_id",  d.getString("adgroup_id"));
            row.put("campaign_id", d.getString("campaign_id"));
            row.put("kname",       d.getString("kname"));
            row.put("im",  toDouble(d, "im")); row.put("clk", toDouble(d, "clk"));
            row.put("cst", toDouble(d, "cst")); row.put("cv", toDouble(d, "cv")); row.put("cr", toDouble(d, "cr"));
            result.add(row);
        }
        return result;
    }

    // ─── 키워드별 집계 (필터 포함: adgroupkeyword / campaignkeyword) ─────────

    public List<Map<String, Object>> aggregateByKeywordWithFilter(String advid, String from, String to,
                                                                    String collection, String advField,
                                                                    String filterField, String filterValue) {
        Criteria criteria = Criteria.where(advField).is(advid).and("daily_dt").gte(from).lte(to);
        if (filterField != null && !filterField.isBlank() && filterValue != null && !filterValue.isBlank()) {
            criteria = criteria.and(filterField).is(filterValue);
        }
        Aggregation agg = Aggregation.newAggregation(
            Aggregation.match(criteria),
            Aggregation.group("keyword_id")
                .first("adgroup_id").as("adgroup_id")
                .first("campaign_id").as("campaign_id")
                .first("kname").as("kname")
                .sum("daily_im").as("im").sum("daily_clk").as("clk").sum("daily_cst").as("cst")
                .sum("daily_cv").as("cv").sum("daily_cr").as("cr")
        );
        List<Map<String, Object>> result = new ArrayList<>();
        for (Document d : mongo.aggregate(agg, collection, Document.class).getMappedResults()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("keyword_id",  d.getString("_id"));
            row.put("adgroup_id",  d.getString("adgroup_id"));
            row.put("campaign_id", d.getString("campaign_id"));
            row.put("kname",       d.getString("kname"));
            row.put("im",  toDouble(d, "im")); row.put("clk", toDouble(d, "clk"));
            row.put("cst", toDouble(d, "cst")); row.put("cv", toDouble(d, "cv")); row.put("cr", toDouble(d, "cr"));
            result.add(row);
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
            Aggregation.group("adgroup_id")
                .first("campaign_id").as("campaign_id")
                .sum("daily_im").as("im").sum("daily_clk").as("clk").sum("daily_cst").as("cst")
                .sum("daily_cv").as("cv").sum("daily_cr").as("cr")
        );
        List<Map<String, Object>> result = new ArrayList<>();
        for (Document d : mongo.aggregate(agg, collection, Document.class).getMappedResults()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("adgroup_id",  d.getString("_id"));
            row.put("campaign_id", d.getString("campaign_id"));
            row.put("im",  toDouble(d, "im")); row.put("clk", toDouble(d, "clk"));
            row.put("cst", toDouble(d, "cst")); row.put("cv", toDouble(d, "cv")); row.put("cr", toDouble(d, "cr"));
            result.add(row);
        }
        return result;
    }

    // ─── 캠페인별 집계 (campaignreport) ─────────────────────────────────────

    public List<Map<String, Object>> aggregateByCampaign(String advid, String from, String to, String collection, String advField) {
        Aggregation agg = Aggregation.newAggregation(
            Aggregation.match(Criteria.where(advField).is(advid)
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

    // ─── campaign_id × date 집계 (groups 주차별 breakdown용) ───────────────

    /** campaign_id × daily_dt 기준 집계 → key: "cid|date" → {im,clk,cst,cv,cr} */
    public Map<String, Map<String, Object>> aggregateByCampaignDate(
            String advid, String from, String to, String collection, String advField) {
        Aggregation agg = Aggregation.newAggregation(
            Aggregation.match(Criteria.where(advField).is(advid)
                .and("daily_dt").gte(from).lte(to)),
            Aggregation.group(Aggregation.fields()
                    .and("campaign_id", "campaign_id")
                    .and("daily_dt",    "daily_dt"))
                .sum("daily_im").as("im")
                .sum("daily_clk").as("clk")
                .sum("daily_cst").as("cst")
                .sum("daily_cv").as("cv")
                .sum("daily_cr").as("cr")
        );
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (Document d : mongo.aggregate(agg, collection, Document.class).getMappedResults()) {
            Document id = (Document) d.get("_id");
            if (id == null) continue;
            String cid  = id.getString("campaign_id");
            String date = id.getString("daily_dt");
            if (date == null) continue;
            if (cid == null) cid = "__null_campaign__"; // null campaign_id도 포함 → unknown 그룹으로 매핑
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("im",  toDouble(d, "im"));
            row.put("clk", toDouble(d, "clk"));
            row.put("cst", toDouble(d, "cst"));
            row.put("cv",  toDouble(d, "cv"));
            row.put("cr",  toDouble(d, "cr"));
            result.put(cid + "|" + date, row);
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

    /** naver_keyword: advkey 기준 kword 목록 조회 (keywordrereport 중복 필터용) */
    public List<String> findNaverKeywordNames(String advkey) {
        org.springframework.data.mongodb.core.query.Query q =
            new org.springframework.data.mongodb.core.query.Query(
                Criteria.where("advkey").is(advkey));
        q.fields().include("kword").exclude("_id");
        return mongo.find(q, Document.class, "naver_keyword").stream()
            .map(d -> d.getString("kword"))
            .filter(s -> s != null && !s.isBlank())
            .collect(java.util.stream.Collectors.toList());
    }

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

    // ─── 시간별 집계 (periodreport hour mode) ────────────────────────────────

    /** hour 컬렉션(adv_id, hour_dt, hour_XX_im/clk/cst/cv/cr) → date → hour → {im,clk,cst,cv,cr} */
    public Map<String, Map<String, Map<String, Object>>> aggregateHourByDate(
            String advid, String from, String to, String collection) {
        if (advid == null || advid.isBlank()) return Collections.emptyMap();
        org.springframework.data.mongodb.core.query.Query q =
            new org.springframework.data.mongodb.core.query.Query(
                Criteria.where("adv_id").is(advid).and("hour_dt").gte(from).lte(to));
        List<Document> docs = mongo.find(q, Document.class, collection);

        Map<String, Map<String, Map<String, Object>>> result = new LinkedHashMap<>();
        for (Document doc : docs) {
            String date = doc.getString("hour_dt");
            if (date == null) continue;
            Map<String, Map<String, Object>> hourMap =
                result.computeIfAbsent(date, k -> new LinkedHashMap<>());
            for (int h = 0; h < 24; h++) {
                String hh = String.format("%02d", h);
                Map<String, Object> row = hourMap.computeIfAbsent(hh, k -> new LinkedHashMap<>());
                row.merge("im",  numVal(doc, "hour_" + hh + "_im"),  (a, b) -> ((Number)a).doubleValue() + ((Number)b).doubleValue());
                row.merge("clk", numVal(doc, "hour_" + hh + "_clk"), (a, b) -> ((Number)a).doubleValue() + ((Number)b).doubleValue());
                row.merge("cst", numVal(doc, "hour_" + hh + "_cst"), (a, b) -> ((Number)a).doubleValue() + ((Number)b).doubleValue());
                row.merge("cv",  numVal(doc, "hour_" + hh + "_cv"),  (a, b) -> ((Number)a).doubleValue() + ((Number)b).doubleValue());
                row.merge("cr",  numVal(doc, "hour_" + hh + "_cr"),  (a, b) -> ((Number)a).doubleValue() + ((Number)b).doubleValue());
            }
        }
        return result;
    }

    private double numVal(Document d, String key) {
        Object v = d.get(key);
        return v instanceof Number ? ((Number) v).doubleValue() : 0.0;
    }

    // ─── 유틸 ────────────────────────────────────────────────────────────────

    private double toDouble(Document d, String key) {
        Object v = d.get(key);
        if (v == null) return 0.0;
        if (v instanceof Number) return ((Number) v).doubleValue();
        return 0.0;
    }

    /** kakao_* 컬렉션은 "advkey", 나머지는 "daily_advid" 로 어드버타이저 식별 */
    private String getAdvField(String collection) {
        return (collection != null && collection.startsWith("kakao_")) ? "advkey" : "daily_advid";
    }

    private Map<String, Object> emptyTotals() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("im", 0.0); r.put("clk", 0.0); r.put("cst", 0.0);
        r.put("cv", 0.0); r.put("cr", 0.0);
        return r;
    }
}
