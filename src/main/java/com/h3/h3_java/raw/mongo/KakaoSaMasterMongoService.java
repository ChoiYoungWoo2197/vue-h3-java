package com.h3.h3_java.raw.mongo;

import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class KakaoSaMasterMongoService {

    private final MongoTemplate mongoTemplate;

    // ── Upsert ────────────────────────────────────────────────────────────────

    public void upsertCampaign(Map<String, Object> doc) {
        upsert("kakao_sa_campaign", doc, "cid");
    }

    public void upsertAdGroup(Map<String, Object> doc) {
        upsert("kakao_sa_adgroup", doc, "gid");
    }

    public void upsertKeyword(Map<String, Object> doc) {
        upsert("kakao_sa_keyword", doc, "kid");
    }

    public void upsertAd(Map<String, Object> doc) {
        upsert("kakao_sa_ad", doc, "aid");
    }

    // ── Read (통계 Job에서 마스터 목록 조회) ────────────────────────────────────

    public List<Map<String, Object>> findCampaigns(String advkey) {
        Query q = Query.query(Criteria.where("advkey").is(advkey));
        return mongoTemplate.find(q, Document.class, "kakao_sa_campaign")
            .stream()
            .map(d -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("cid",    d.getString("cid"));
                m.put("cname",  d.getString("cname"));
                m.put("onoff",  d.getInteger("onoff", 0));
                return m;
            })
            .collect(Collectors.toList());
    }

    public List<Map<String, Object>> findAdGroups(String advkey) {
        Query q = Query.query(Criteria.where("advkey").is(advkey));
        return mongoTemplate.find(q, Document.class, "kakao_sa_adgroup")
            .stream()
            .map(d -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("cid",    d.getString("cid"));
                m.put("gid",    d.getString("gid"));
                m.put("gname",  d.getString("gname"));
                m.put("onoff",  d.getInteger("onoff", 0));
                return m;
            })
            .collect(Collectors.toList());
    }

    public List<Map<String, Object>> findKeywords(String advkey) {
        Query q = Query.query(Criteria.where("advkey").is(advkey));
        return mongoTemplate.find(q, Document.class, "kakao_sa_keyword")
            .stream()
            .map(d -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("gid",     d.getString("gid"));
                m.put("kid",     d.getString("kid"));
                m.put("kname",   d.getString("kname"));
                m.put("onoff",   d.getInteger("onoff", 0));
                return m;
            })
            .collect(Collectors.toList());
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private void upsert(String collection, Map<String, Object> doc, String idField) {
        Query q = Query.query(Criteria.where(idField).is(doc.get(idField)));
        Update u = new Update();
        doc.forEach(u::set);
        mongoTemplate.upsert(q, u, collection);
    }
}
