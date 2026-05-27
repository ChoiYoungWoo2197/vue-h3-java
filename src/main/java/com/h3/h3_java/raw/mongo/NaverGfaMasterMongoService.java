package com.h3.h3_java.raw.mongo;

import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
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

    public Set<String> selectGfaCampaignIds(String advkey) {
        Query query = Query.query(Criteria.where("advkey").is(advkey));
        query.fields().include("cid").exclude("_id");
        return mongoTemplate.find(query, Document.class, "naver_gfa_campaign")
            .stream().map(d -> d.getString("cid")).filter(Objects::nonNull)
            .collect(Collectors.toSet());
    }

    private void upsert(String collection, Map<String, Object> data, String key1, String key2) {
        Criteria criteria = Criteria.where(key1).is(data.get(key1)).and(key2).is(data.get(key2));
        Update update = new Update();
        data.forEach(update::set);
        mongoTemplate.upsert(Query.query(criteria), update, collection);
    }
}
