package com.h3.h3_java.api.controller;

import com.h3.h3_java.api.mapper.AlarmMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Slf4j
@RestController
@RequestMapping("/v1/h3/app/alarm")
@RequiredArgsConstructor
public class AlarmController {

    private final AlarmMapper alarmMapper;

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

        Map<String, Object> q = new HashMap<>();
        q.put("userId",   userId);
        q.put("fromdate", fromdate.isBlank() ? null : fromdate);
        q.put("todate",   todate.isBlank()   ? null : todate);
        q.put("offset",   offset);
        q.put("limit",    display);

        List<Map<String, Object>> rows;
        int totalcount;

        if ("admin".equals(mode)) {
            rows       = alarmMapper.selectAlarmsForAdmin(q);
            totalcount = alarmMapper.countAlarmsForAdmin(q);
        } else {
            rows       = alarmMapper.selectAlarmsForUser(q);
            totalcount = alarmMapper.countAlarmsForUser(q);
        }

        List<Map<String, Object>> alarms = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> alarm = new LinkedHashMap<>(row);
            String media = str(row.get("media"));
            String type  = str(row.get("type"));
            alarm.put("media", MEDIA_SET.getOrDefault(media, media));
            alarm.put("type",  TYPE_SET.getOrDefault(type, type));
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
        Map<String, Object> row = alarmMapper.selectAlarmSetting(userId);

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

        Map<String, Object> existing = alarmMapper.selectAlarmSetting(userId);

        Map<String, Object> row = new HashMap<>();
        row.put("userId", userId);
        row.put("type",   type);
        row.put("im",     im);
        row.put("clk",    clk);
        row.put("cv",     cv);
        row.put("cr",     cr);

        if (existing == null) {
            alarmMapper.insertAlarmSetting(row);
        } else {
            alarmMapper.updateAlarmSetting(row);
        }

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
