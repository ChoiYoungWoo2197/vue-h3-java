package com.h3.h3_java.raw.mongo;

import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ReportMongoService {

    private final MongoTemplate mongo;
    private static final String COL = "h3_report";

    public int count(String userId) {
        return (int) mongo.count(Query.query(Criteria.where("user_id").is(userId)), COL);
    }

    public List<Map<String, Object>> find(String userId, int skip, int limit) {
        Query q = Query.query(Criteria.where("user_id").is(userId))
                .with(Sort.by(Sort.Direction.DESC, "_id"))
                .skip(skip)
                .limit(limit);
        List<Document> docs = mongo.find(q, Document.class, COL);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Document doc : docs) {
            result.add(toMap(doc));
        }
        return result;
    }

    public void insert(Map<String, Object> data) {
        mongo.insert(new Document(data), COL);
    }

    public void updatePdfDate(String id, String pdfdate) {
        Query q = Query.query(Criteria.where("_id").is(new ObjectId(id)));
        mongo.updateFirst(q, Update.update("pdfdate", pdfdate), COL);
    }

    // MongoDB 저장 키(소문자) → Vue가 기대하는 camelCase 키 매핑
    private static final Map<String, String> RENAMES = Map.of(
        "mediaanalysis",    "mediaAnalysis",
        "campaignanalysis", "campaignAnalysis",
        "periodanalysis",   "periodAnalysis",
        "keywordanalysis",  "keywordAnalysis",
        "adanalysis",       "adAnalysis",
        "shoppinganalysis", "shoppingAnalysis"
    );

    // MySQL zero-date(0000-00-00) → JDBC → java.util.Date(2000-01-01) 등 무효 날짜 처리 대상
    private static final Set<String> DATE_FIELDS = Set.of("sendate", "pdfdate", "daily_regdate");

    // _id(ObjectId) → daily_seq(hex string) 으로 치환; daily_seq 원본 필드는 제거
    private Map<String, Object> toMap(Document doc) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("daily_seq", doc.getObjectId("_id").toHexString());
        for (Map.Entry<String, Object> e : doc.entrySet()) {
            if ("_id".equals(e.getKey()) || "daily_seq".equals(e.getKey())) continue;
            String key = RENAMES.getOrDefault(e.getKey(), e.getKey());
            Object val = DATE_FIELDS.contains(e.getKey()) ? normalizeDate(e.getValue()) : e.getValue();
            map.put(key, val);
        }
        return map;
    }

    private Object normalizeDate(Object val) {
        if (val == null) return null;
        if (val instanceof Date d) {
            Calendar c = Calendar.getInstance();
            c.setTime(d);
            // MySQL zero-date → JDBC → java.util.Date ≈ 2000-01-01 → null(Vue에서 "-" 표시)
            if (c.get(Calendar.YEAR) <= 2001) return null;
            return d.toInstant().atZone(ZoneId.of("Asia/Seoul")).toLocalDate().toString();
        }
        if (val instanceof String s) {
            if (s.isEmpty() || s.equals("0") || s.startsWith("0000-")) return null;
            return s;
        }
        if (val instanceof Number n && n.longValue() == 0L) return null;
        return val;
    }
}
