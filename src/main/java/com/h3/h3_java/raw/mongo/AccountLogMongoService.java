package com.h3.h3_java.raw.mongo;

import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class AccountLogMongoService {

    private final MongoTemplate mongo;
    private static final String COL = "h3_account_log";
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public Document findByAdvkey(String advkey) {
        if (advkey == null || advkey.isBlank()) return null;
        return mongo.findOne(Query.query(Criteria.where("advkey").is(advkey)), Document.class, COL);
    }

    /** 배치 Job 완료 후 해당 필드 타임스탬프 갱신 */
    public void updateField(String advkey, String media, String fieldName) {
        if (advkey == null || advkey.isBlank()) return;
        String now = LocalDateTime.now().format(DT_FMT);
        Query q = Query.query(Criteria.where("advkey").is(advkey));
        Update u = new Update()
                .set(fieldName, now)
                .setOnInsert("advkey", advkey)
                .setOnInsert("media",  media);
        mongo.upsert(q, u, COL);
    }

    /** 이관용 */
    public void upsert(Document doc) {
        Query q = Query.query(Criteria.where("advkey").is(doc.getString("advkey")));
        mongo.upsert(q, Update.fromDocument(doc), COL);
    }
}
