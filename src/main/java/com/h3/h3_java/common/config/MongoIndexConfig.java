package com.h3.h3_java.common.config;

import com.mongodb.client.model.IndexOptions;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class MongoIndexConfig {

    private final MongoTemplate mongoTemplate;

    @PostConstruct
    public void createIndexes() {
        log.info("[MONGO INDEX] 인덱스 생성 시작");

        // ── Master ────────────────────────────────────────────────────────────
        unique("naver_campaign",         "advkey", "campaignid");
        unique("naver_campaign_budget",  "advkey", "campaignid");
        unique("naver_adgroup",          "advkey", "gid");
        unique("naver_adgroup_budget",   "advkey", "gid");
        unique("naver_adextension",      "advkey", "extid");
        unique("naver_keyword",          "advkey", "kwid");
        unique("naver_ad",               "advkey", "adid");
        unique("naver_shopping_product", "advkey", "adid");

        // adextension: type + ownerid 조회용 (selectGroupExtIds)
        index("naver_adextension", "type", "ownerid");

        // delta 추적
        unique("naver_master_delta", "deltakey", "name", "userid");

        // ── 시간별 ─────────────────────────────────────────────────────────────
        unique("naver_campaign_hour", "adv_id", "hour_dt");

        // ── 일별 통계 ──────────────────────────────────────────────────────────
        unique("naver_campaign_daily",     "daily_advid", "daily_dt", "campaign_id");
        unique("naver_adgroup_daily",      "daily_advid", "daily_dt", "adgroup_id");
        unique("naver_ad_daily",           "daily_advid", "daily_dt", "ad_id");
        unique("naver_shopping_ad_daily",  "daily_advid", "daily_dt", "ad_id");
        unique("naver_keyword_daily",      "daily_advid", "daily_dt", "keyword_id");

        // target: upsert 키가 13개 필드 → existsBy 쿼리(daily_advid+daily_dt)만 커버
        index("naver_target_daily", "daily_advid", "daily_dt");

        // ── 전환유형 ───────────────────────────────────────────────────────────
        unique("naver_campaign_convtype", "daily_advid", "daily_dt", "campaign_id", "conv_type_code");
        unique("naver_adgroup_convtype",  "daily_advid", "daily_dt", "adgroup_id",  "conv_type_code");
        unique("naver_keyword_convtype",  "daily_advid", "daily_dt", "keyword_id",  "conv_type_code");
        unique("naver_ad_convtype",       "daily_advid", "daily_dt", "ad_id",       "conv_type_code");

        log.info("[MONGO INDEX] 인덱스 생성 완료");
    }

    private void unique(String collection, String... fields) {
        ensureIndex(collection, true, fields);
    }

    private void index(String collection, String... fields) {
        ensureIndex(collection, false, fields);
    }

    private void ensureIndex(String collection, boolean unique, String... fields) {
        try {
            Document keys = new Document();
            for (String f : fields) keys.append(f, 1);
            mongoTemplate.getCollection(collection)
                .createIndex(keys, new IndexOptions().unique(unique));
        } catch (Exception e) {
            log.warn("[MONGO INDEX] {} 인덱스 생성 실패: {}", collection, e.getMessage());
        }
    }
}
