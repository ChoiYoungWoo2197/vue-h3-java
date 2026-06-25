package com.h3.h3_java.api.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** MySQL h3_budget_alarm / h3_budget_daily_alarm → MongoDB 일회성 이관 */
@Slf4j
@RestController
@RequestMapping("/api/collector/migrate")
@RequiredArgsConstructor
public class AlarmMigrateController {

    private final JdbcTemplate  jdbc;
    private final MongoTemplate mongo;

    private static final DateTimeFormatter DT_FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @PostMapping("/budget-alarm")
    public Map<String, Object> migrateBudgetAlarm() {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM h3_budget_alarm");
        int count = 0;
        for (Map<String, Object> row : rows) {
            Document doc = toDocument(row);
            mongo.insert(doc, "h3_budget_alarm");
            count++;
        }
        log.info("[MIGRATE] h3_budget_alarm {} 건 이관 완료", count);
        return Map.of("result", "success", "count", count);
    }

    @PostMapping("/budget-daily-alarm")
    public Map<String, Object> migrateBudgetDailyAlarm() {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM h3_budget_daily_alarm");
        int count = 0;
        for (Map<String, Object> row : rows) {
            Document doc = toDocument(row);
            mongo.insert(doc, "h3_budget_daily_alarm");
            count++;
        }
        log.info("[MIGRATE] h3_budget_daily_alarm {} 건 이관 완료", count);
        return Map.of("result", "success", "count", count);
    }

    private Document toDocument(Map<String, Object> row) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : row.entrySet()) {
            Object val = e.getValue();
            if (val instanceof Timestamp ts) {
                val = ts.toLocalDateTime().format(DT_FMT);
            }
            map.put(e.getKey(), val);
        }
        return new Document(map);
    }
}
