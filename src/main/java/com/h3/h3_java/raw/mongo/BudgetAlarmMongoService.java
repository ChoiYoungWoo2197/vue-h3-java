package com.h3.h3_java.raw.mongo;

import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BudgetAlarmMongoService {

    private final MongoTemplate mongo;

    private static final String ALARM     = "h3_budget_alarm";
    private static final String ALARM_SET = "h3_budget_daily_alarm";
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // ── h3_budget_alarm ───────────────────────────────────────────────────────

    public List<Document> findAlarmsForUser(String userId, String fromdate, String todate,
                                            int offset, int limit) {
        Query q = buildUserQuery(userId, fromdate, todate)
            .with(Sort.by(Sort.Direction.DESC, "daily_dt"))
            .skip(offset).limit(limit);
        return mongo.find(q, Document.class, ALARM);
    }

    public long countAlarmsForUser(String userId, String fromdate, String todate) {
        return mongo.count(buildUserQuery(userId, fromdate, todate), ALARM);
    }

    public List<Document> findAllAlarms(String fromdate, String todate, int offset, int limit) {
        Query q = buildDateQuery(fromdate, todate)
            .with(Sort.by(Sort.Direction.DESC, "daily_dt"))
            .skip(offset).limit(limit);
        return mongo.find(q, Document.class, ALARM);
    }

    public long countAllAlarms(String fromdate, String todate) {
        return mongo.count(buildDateQuery(fromdate, todate), ALARM);
    }

    /** 비즈머니 잠금 알람 중복 체크 (최근 1일) */
    public long countRecentBizmoneyAlarm(String userId, String advid) {
        String threshold = LocalDateTime.now().minusDays(1).format(DT_FMT);
        Query q = Query.query(
            Criteria.where("user_id").is(userId)
                .and("daily_advid").is(advid)
                .and("daily_dt").gte(threshold)
                .and("media").is("naver")
                .and("type").is("bizmoney_locked_by_budget")
        );
        return mongo.count(q, ALARM);
    }

    /** 계정/캠페인 KPI 알람 중복 체크 */
    public long countRecentAlarm(String userId, String advid, String media,
                                  String level, String targetId, String type, int withinDays) {
        String threshold = LocalDateTime.now().minusDays(withinDays).format(DT_FMT);
        Criteria c = Criteria.where("user_id").is(userId)
            .and("daily_advid").is(advid)
            .and("daily_dt").gte(threshold)
            .and("media").is(media)
            .and("level").is(level)
            .and("type").is(type);
        if (targetId != null) {
            c = c.and("target_id").is(targetId);
        }
        return mongo.count(Query.query(c), ALARM);
    }

    public void insertAlarm(Map<String, Object> row) {
        mongo.insert(new Document(row), ALARM);
    }

    // ── h3_budget_daily_alarm ─────────────────────────────────────────────────

    public Document findSetting(String userId) {
        return mongo.findOne(
            Query.query(Criteria.where("user_id").is(userId)),
            Document.class, ALARM_SET
        );
    }

    public void upsertSetting(String userId, int type, String im, String clk, String cv, String cr) {
        Query q = Query.query(Criteria.where("user_id").is(userId));
        Update u = new Update()
            .set("user_id", userId)
            .set("type", type)
            .set("im",   im)
            .set("clk",  clk)
            .set("cv",   cv)
            .set("cr",   cr);
        mongo.upsert(q, u, ALARM_SET);
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private Query buildUserQuery(String userId, String fromdate, String todate) {
        Criteria c = Criteria.where("user_id").is(userId);
        if (fromdate != null && !fromdate.isBlank()) {
            c = c.and("daily_dt").gte(fromdate).lte(todate);
        }
        return Query.query(c);
    }

    private Query buildDateQuery(String fromdate, String todate) {
        if (fromdate != null && !fromdate.isBlank()) {
            return Query.query(Criteria.where("daily_dt").gte(fromdate).lte(todate));
        }
        return new Query();
    }
}
