package com.h3.h3_java.api.controller;

import com.h3.h3_java.raw.mongo.BudgetAlarmMongoService;
import com.h3.h3_java.raw.mongo.ShareMongoService;
import com.h3.h3_java.raw.mongo.UserMongoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/v1/h3/app/alarm")
@RequiredArgsConstructor
public class AlarmController {

    private final BudgetAlarmMongoService budgetAlarmMongo;
    private final ShareMongoService       shareMongo;
    private final UserMongoService        userMongo;

    private static final Map<String, String> MEDIA_SET = Map.of(
        "naver",   "네이버검색광고",
        "kakaosa", "카카오검색광고",
        "naverda", "네이버디스플레이",
        "kakaomo", "카카오모먼트"
    );

    private static final Map<String, String> TYPE_SET = Map.ofEntries(
        Map.entry("bizmoney_locked_by_budget",   "계정 비즈머니 중단"),
        Map.entry("campaign_limited_by_budget",  "캠페인 하루예산 중단"),
        Map.entry("adgroup_limited_by_budget",   "광고그룹 하루예산 중단"),
        Map.entry("account_rate_updown_by_clk",  "계정 클릭수 증감률"),
        Map.entry("account_rate_updown_by_im",   "계정 노출수 증감률"),
        Map.entry("account_rate_updown_by_cv",   "계정 전환수 증감률"),
        Map.entry("account_rate_updown_by_cr",   "계정 전환매출 증감률"),
        Map.entry("campaign_rate_updown_by_clk", "캠페인 클릭수 증감률"),
        Map.entry("campaign_rate_updown_by_im",  "캠페인 노출수 증감률"),
        Map.entry("campaign_rate_updown_by_cv",  "캠페인 전환수 증감률"),
        Map.entry("campaign_rate_updown_by_cr",  "캠페인 전환매출 증감률")
    );

    // ── GET /app/alarm/alarm ─────────────────────────────────────────────────
    @GetMapping("/alarm")
    public Map<String, Object> alarm(@RequestParam Map<String, String> params) {
        String userId   = params.getOrDefault("userid",   "");
        String mode     = params.getOrDefault("mode",     "user");
        String fromdate = params.getOrDefault("fromdate", "");
        String todate   = params.getOrDefault("todate",   "");
        int    start    = toInt(params.get("start"),   0);
        int    display  = toInt(params.get("display"), 10);
        int    offset   = start * display;

        String fd = fromdate.isBlank() ? null : fromdate;
        String td = todate.isBlank()   ? null : todate;

        boolean isAdmin = "admin".equals(mode);
        List<Document> docs;
        long totalcount;

        if (isAdmin) {
            docs       = budgetAlarmMongo.findAllAlarms(fd, td, offset, display);
            totalcount = budgetAlarmMongo.countAllAlarms(fd, td);
        } else {
            docs       = budgetAlarmMongo.findAlarmsForUser(userId, fd, td, offset, display);
            totalcount = budgetAlarmMongo.countAlarmsForUser(userId, fd, td);
        }

        // admin 모드: h3_share + h3_users JOIN으로 매니저 정보 추가
        Map<String, String> userIdToManagerId = new HashMap<>();
        Map<String, String> managerIdToName   = new HashMap<>();
        if (isAdmin && !docs.isEmpty()) {
            List<String> userIds = docs.stream()
                .map(d -> d.getString("user_id"))
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

            List<Document> shares = shareMongo.findByUserIds(userIds);
            List<String> managerIds = new ArrayList<>();
            for (Document s : shares) {
                String uid = s.getString("user_id");
                String mid = s.getString("user_manager");
                if (uid != null && mid != null) {
                    userIdToManagerId.put(uid, mid);
                    managerIds.add(mid);
                }
            }
            for (String mid : managerIds.stream().distinct().collect(Collectors.toList())) {
                Document u = userMongo.findByUserId(mid);
                if (u != null) {
                    managerIdToName.put(mid, u.getString("user_name"));
                }
            }
        }

        List<Map<String, Object>> alarms = new ArrayList<>();
        for (Document doc : docs) {
            Map<String, Object> alarm = new LinkedHashMap<>(doc);
            alarm.remove("_id");
            String media = str(doc.get("media"));
            String type  = str(doc.get("type"));
            alarm.put("media", MEDIA_SET.getOrDefault(media, media));
            alarm.put("type",  TYPE_SET.getOrDefault(type, type));
            if (isAdmin) {
                String uid = doc.getString("user_id");
                String mid = userIdToManagerId.get(uid);
                alarm.put("manager_id",   mid);
                alarm.put("manager_name", mid != null ? managerIdToName.get(mid) : null);
            }
            alarms.add(alarm);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("alarms", alarms);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("result",      "success");
        res.put("status",      "200");
        res.put("data",        data);
        res.put("resultcount", 0);
        res.put("totalcount",  totalcount);
        return res;
    }

    // ── GET /app/alarm/alarmset ──────────────────────────────────────────────
    @GetMapping("/alarmset")
    public Map<String, Object> alarmset(@RequestParam Map<String, String> params) {
        String userId = params.getOrDefault("userid", "");
        String mode   = params.getOrDefault("mode",   "get");

        if ("upsert".equals(mode)) {
            return upsertAlarmSetting(userId, params);
        }
        return getAlarmSetting(userId);
    }

    private Map<String, Object> getAlarmSetting(String userId) {
        Document row = budgetAlarmMongo.findSetting(userId);

        List<Map<String, Object>> alarmset = new ArrayList<>();
        if (row != null) {
            Map<String, Object> item = new LinkedHashMap<>();
            Object typeVal = row.get("type");
            item.put("type", typeVal instanceof Number ? ((Number) typeVal).intValue() != 0 : Boolean.TRUE.equals(typeVal));
            item.put("im",  emptyStr(row.get("im")));
            item.put("clk", emptyStr(row.get("clk")));
            item.put("cv",  emptyStr(row.get("cv")));
            item.put("cr",  emptyStr(row.get("cr")));
            alarmset.add(item);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("alarmset", alarmset);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("result",      "success");
        res.put("status",      "200");
        res.put("data",        data);
        res.put("resultcount", 0);
        res.put("totalcount",  0);
        return res;
    }

    private Map<String, Object> upsertAlarmSetting(String userId, Map<String, String> params) {
        String im   = params.getOrDefault("im",  "");
        String clk  = params.getOrDefault("clk", "");
        String cv   = params.getOrDefault("cv",  "");
        String cr   = params.getOrDefault("cr",  "");
        int    type = "1".equals(params.get("type")) || "true".equalsIgnoreCase(params.get("type")) ? 1 : 0;

        budgetAlarmMongo.upsertSetting(userId, type, im, clk, cv, cr);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("result",      "success");
        res.put("status",      "200");
        res.put("data",        Map.of("alarmset", List.of()));
        res.put("resultcount", 0);
        res.put("totalcount",  0);
        return res;
    }

    private int toInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }

    private String str(Object val) {
        return val == null ? "" : val.toString();
    }

    private String emptyStr(Object val) {
        if (val == null) return "";
        String s = val.toString();
        return s.equals("null") ? "" : s;
    }
}
