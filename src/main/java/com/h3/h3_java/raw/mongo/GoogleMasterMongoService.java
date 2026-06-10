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
public class GoogleMasterMongoService {

    private final MongoTemplate mongoTemplate;

    // ── Upsert ────────────────────────────────────────────────────────────────

    public void upsertCampaign(Map<String, Object> doc) {
        upsert("google_campaign", doc, "cid");
    }

    public void upsertAdGroup(Map<String, Object> doc) {
        upsert("google_adgroup", doc, "gid");
    }

    public void upsertKeyword(Map<String, Object> doc) {
        upsert("google_keyword", doc, "kid");
    }

    public void upsertAd(Map<String, Object> doc) {
        upsert("google_ad", doc, "aid");
    }

    // ── Read (통계 Job에서 마스터 목록 조회) ────────────────────────────────────

    public List<Map<String, Object>> findCampaigns(String advkey) {
        Query q = Query.query(Criteria.where("advkey").is(advkey));
        return mongoTemplate.find(q, Document.class, "google_campaign")
            .stream()
            .map(d -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("cid",   d.getString("cid"));
                m.put("cname", d.getString("cname"));
                m.put("onoff", d.getInteger("onoff", 0));
                return m;
            })
            .collect(Collectors.toList());
    }

    public List<Map<String, Object>> findAdGroups(String advkey) {
        Query q = Query.query(Criteria.where("advkey").is(advkey));
        return mongoTemplate.find(q, Document.class, "google_adgroup")
            .stream()
            .map(d -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("cid",  d.getString("cid"));
                m.put("gid",  d.getString("gid"));
                return m;
            })
            .collect(Collectors.toList());
    }

    public List<Map<String, Object>> findAds(String advkey) {
        Map<String, String> gidToCid = buildAdgroupCampaignMap(advkey);
        Query q = Query.query(Criteria.where("advkey").is(advkey));
        return mongoTemplate.find(q, Document.class, "google_ad")
            .stream()
            .map(d -> {
                String gid = d.getString("gid");
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("aid",         d.getString("aid"));
                m.put("adgroup_id",  gid != null ? gid : "");
                m.put("campaign_id", gidToCid.getOrDefault(gid, ""));
                return m;
            })
            .collect(Collectors.toList());
    }

    public List<Map<String, Object>> findKeywords(String advkey) {
        Map<String, String> gidToCid = buildAdgroupCampaignMap(advkey);
        Query q = Query.query(Criteria.where("advkey").is(advkey));
        return mongoTemplate.find(q, Document.class, "google_keyword")
            .stream()
            .map(d -> {
                String gid = d.getString("gid");
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("kid",         d.getString("kid"));
                m.put("adgroup_id",  gid != null ? gid : "");
                m.put("campaign_id", gidToCid.getOrDefault(gid, ""));
                return m;
            })
            .collect(Collectors.toList());
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private Map<String, String> buildAdgroupCampaignMap(String advkey) {
        Query q = Query.query(Criteria.where("advkey").is(advkey));
        return mongoTemplate.find(q, Document.class, "google_adgroup")
            .stream()
            .filter(d -> d.getString("gid") != null)
            .collect(Collectors.toMap(
                d -> d.getString("gid"),
                d -> d.getString("cid") != null ? d.getString("cid") : "",
                (a, b) -> a
            ));
    }

    public boolean hasCampaignData(String advkey) {
        return mongoTemplate.exists(Query.query(Criteria.where("advkey").is(advkey)), "google_campaign");
    }

    private void upsert(String collection, Map<String, Object> doc, String idField) {
        Query q = Query.query(Criteria.where(idField).is(doc.get(idField)));
        Update u = new Update();
        doc.forEach(u::set);
        mongoTemplate.upsert(q, u, collection);
    }
}
