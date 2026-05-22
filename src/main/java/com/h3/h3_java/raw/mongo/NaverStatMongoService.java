package com.h3.h3_java.raw.mongo;

import com.h3.h3_java.media.naver.dto.NaverAdDto;
import com.h3.h3_java.media.naver.dto.NaverAdGroupDto;
import com.h3.h3_java.media.naver.dto.NaverShoppingAdDto;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NaverStatMongoService {

    private final MongoTemplate mongoTemplate;

    // =====================================================================
    // Campaign Daily (기존)
    // =====================================================================

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
        return mongoTemplate.exists(Query.query(
            Criteria.where("daily_advid").is(customerId).and("daily_dt").is(date)
        ), "naver_campaign_daily");
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
        return mongoTemplate.exists(Query.query(
            Criteria.where("adv_id").is(advId).and("hour_dt").is(date)
        ), "naver_campaign_hour");
    }

    public List<Document> selectCampaignsByCustomer(String customerId) {
        return mongoTemplate.find(Query.query(Criteria.where("advkey").is(customerId)),
            Document.class, "naver_campaign");
    }

    // =====================================================================
    // Master Data 조회 (naver_adgroup / naver_ad / naver_shopping_product / naver_keyword)
    // =====================================================================

    public List<NaverAdGroupDto> selectAdGroupsByCustomer(String customerId) {
        Query query = Query.query(Criteria.where("advkey").is(customerId));
        query.fields().include("gid").include("cid").exclude("_id");
        return mongoTemplate.find(query, Document.class, "naver_adgroup").stream()
            .filter(d -> d.getString("gid") != null && !d.getString("gid").isEmpty())
            .map(d -> {
                NaverAdGroupDto dto = new NaverAdGroupDto();
                dto.setAdgroupId(d.getString("gid"));
                dto.setCampaignId(d.getString("cid") != null ? d.getString("cid") : "");
                return dto;
            }).collect(Collectors.toList());
    }

    public List<NaverAdDto> selectAdsByCustomer(String customerId) {
        Map<String, String> agMap = buildAdgroupCampaignMap(customerId);
        Query query = Query.query(Criteria.where("advkey").is(customerId));
        query.fields().include("adid").include("groupid").exclude("_id");
        return mongoTemplate.find(query, Document.class, "naver_ad").stream()
            .filter(d -> d.getString("adid") != null && !d.getString("adid").isEmpty())
            .map(d -> {
                NaverAdDto dto = new NaverAdDto();
                String groupid = d.getString("groupid");
                dto.setAdId(d.getString("adid"));
                dto.setAdgroupId(groupid != null ? groupid : "");
                dto.setCampaignId(agMap.getOrDefault(groupid, ""));
                return dto;
            }).collect(Collectors.toList());
    }

    public List<NaverShoppingAdDto> selectShoppingAdsByCustomer(String customerId) {
        Map<String, String> agMap = buildAdgroupCampaignMap(customerId);
        Query query = Query.query(Criteria.where("advkey").is(customerId));
        query.fields().include("adid").include("groupid").include("pid").exclude("_id");
        return mongoTemplate.find(query, Document.class, "naver_shopping_product").stream()
            .filter(d -> d.getString("adid") != null && !d.getString("adid").isEmpty())
            .map(d -> {
                NaverShoppingAdDto dto = new NaverShoppingAdDto();
                String groupid = d.getString("groupid");
                dto.setAdId(d.getString("adid"));
                dto.setAdgroupId(groupid != null ? groupid : "");
                dto.setCampaignId(agMap.getOrDefault(groupid, ""));
                dto.setAdPid(d.getString("pid") != null ? d.getString("pid") : "");
                return dto;
            }).collect(Collectors.toList());
    }

    public List<Map<String, Object>> selectKeywordMapping(String customerId) {
        Map<String, String> agMap = buildAdgroupCampaignMap(customerId);
        Query query = Query.query(Criteria.where("advkey").is(customerId));
        query.fields().include("kwid").include("adgroupid").exclude("_id");
        return mongoTemplate.find(query, Document.class, "naver_keyword").stream()
            .filter(d -> d.getString("kwid") != null && !d.getString("kwid").isEmpty())
            .map(d -> {
                String agid = d.getString("adgroupid");
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("keywordId",  d.getString("kwid"));
                m.put("adgroupId",  agid != null ? agid : "");
                m.put("campaignId", agMap.getOrDefault(agid, ""));
                return m;
            }).collect(Collectors.toList());
    }

    // =====================================================================
    // Gap 체크 (has* 메서드)
    // =====================================================================

    public boolean hasAdGroupDailyData(String customerId, String date) {
        return existsBy("naver_adgroup_daily", customerId, date);
    }

    public boolean hasAdDailyData(String customerId, String date) {
        return existsBy("naver_ad_daily", customerId, date);
    }

    public boolean hasKeywordDailyData(String customerId, String date) {
        return existsBy("naver_keyword_daily", customerId, date);
    }

    public boolean hasTargetDailyData(String customerId, String date) {
        return existsBy("naver_target_daily", customerId, date);
    }

    public boolean hasCampaignConvTypeData(String customerId, String date) {
        return existsBy("naver_campaign_convtype", customerId, date);
    }

    public boolean hasAdGroupConvTypeData(String customerId, String date) {
        return existsBy("naver_adgroup_convtype", customerId, date);
    }

    public boolean hasKeywordConvTypeData(String customerId, String date) {
        return existsBy("naver_keyword_convtype", customerId, date);
    }

    public boolean hasAdConvTypeData(String customerId, String date) {
        return existsBy("naver_ad_convtype", customerId, date);
    }

    // =====================================================================
    // 단건 Upsert
    // =====================================================================

    public void upsertAdGroupDaily(Map<String, Object> row) {
        upsertRow("naver_adgroup_daily", row, "daily_advid", "daily_dt", "adgroup_id");
    }

    public void upsertAdDaily(Map<String, Object> row) {
        upsertRow("naver_ad_daily", row, "daily_advid", "daily_dt", "ad_id");
    }

    public void upsertShoppingAdDaily(Map<String, Object> row) {
        upsertRow("naver_shopping_ad_daily", row, "daily_advid", "daily_dt", "ad_id");
    }

    public void upsertKeywordDaily(Map<String, Object> row) {
        upsertRow("naver_keyword_daily", row, "daily_advid", "daily_dt", "keyword_id");
    }

    // =====================================================================
    // Bulk Upsert
    // =====================================================================

    public void bulkUpsertTargetRows(List<Map<String, Object>> rows) {
        bulkUpsert("naver_target_daily", rows,
            "daily_advid", "daily_dt", "campaign_id", "adgroup_id",
            "keyword_id", "ad_id", "bc_id", "hours", "rcode", "mcode", "pmtype", "cvmethod", "cvtype");
    }

    public void bulkUpsertCampaignConvType(List<Map<String, Object>> rows) {
        bulkUpsert("naver_campaign_convtype", rows,
            "daily_advid", "daily_dt", "campaign_id", "conv_type_code");
    }

    public void bulkUpsertAdGroupConvType(List<Map<String, Object>> rows) {
        bulkUpsert("naver_adgroup_convtype", rows,
            "daily_advid", "daily_dt", "adgroup_id", "conv_type_code");
    }

    public void bulkUpsertKeywordConvType(List<Map<String, Object>> rows) {
        bulkUpsert("naver_keyword_convtype", rows,
            "daily_advid", "daily_dt", "keyword_id", "conv_type_code");
    }

    public void bulkUpsertAdConvType(List<Map<String, Object>> rows) {
        bulkUpsert("naver_ad_convtype", rows,
            "daily_advid", "daily_dt", "ad_id", "conv_type_code");
    }

    // =====================================================================
    // Private Helpers
    // =====================================================================

    private Map<String, String> buildAdgroupCampaignMap(String customerId) {
        Query query = Query.query(Criteria.where("advkey").is(customerId));
        query.fields().include("gid").include("cid").exclude("_id");
        return mongoTemplate.find(query, Document.class, "naver_adgroup").stream()
            .filter(d -> d.getString("gid") != null)
            .collect(Collectors.toMap(
                d -> d.getString("gid"),
                d -> d.getString("cid") != null ? d.getString("cid") : "",
                (a, b) -> a
            ));
    }

    private boolean existsBy(String collection, String customerId, String date) {
        return mongoTemplate.exists(Query.query(
            Criteria.where("daily_advid").is(customerId).and("daily_dt").is(date)
        ), collection);
    }

    private void upsertRow(String collection, Map<String, Object> row, String... keyFields) {
        Criteria criteria = Criteria.where(keyFields[0]).is(row.get(keyFields[0]));
        for (int i = 1; i < keyFields.length; i++) {
            criteria = criteria.and(keyFields[i]).is(row.get(keyFields[i]));
        }
        Update update = new Update();
        row.forEach(update::set);
        mongoTemplate.upsert(Query.query(criteria), update, collection);
    }

    private void bulkUpsert(String collection, List<Map<String, Object>> rows, String... keyFields) {
        if (rows.isEmpty()) return;
        BulkOperations bulk = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, collection);
        for (Map<String, Object> row : rows) {
            Criteria criteria = Criteria.where(keyFields[0]).is(row.get(keyFields[0]));
            for (int i = 1; i < keyFields.length; i++) {
                criteria = criteria.and(keyFields[i]).is(row.get(keyFields[i]));
            }
            Update update = new Update();
            row.forEach(update::set);
            bulk.upsert(Query.query(criteria), update);
        }
        bulk.execute();
    }
}
