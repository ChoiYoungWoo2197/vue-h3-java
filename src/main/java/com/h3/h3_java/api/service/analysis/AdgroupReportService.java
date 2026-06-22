package com.h3.h3_java.api.service.analysis;

import com.h3.h3_java.api.dto.AccountDto;
import com.h3.h3_java.api.mapper.AccountMapper;
import com.h3.h3_java.raw.mongo.DashboardMongoService;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class AdgroupReportService {

    private final AccountMapper accountMapper;
    private final DashboardMongoService mongoService;

    // naverOnoff=true: 0=on(Naver SA, parseInt("ON")→0), false: 1=on(표준)
    record MediaConfig(String dailyCol, String masterCol, String campCol,
                       String advField, String campIdField, String campNameField,
                       boolean naverOnoff) {}

    private static final Map<String, MediaConfig> MD_MAP = Map.of(
        "N",      new MediaConfig("naver_adgroup_daily",     "naver_adgroup",     "naver_campaign",     "daily_advid", "campaignid", "campaignname", true),
        "D",      new MediaConfig("kakao_sa_adgroup_daily",  "kakao_sa_adgroup",  "kakao_sa_campaign",  "advkey",      "cid",        "cname",        false),
        "K",      new MediaConfig("kakao_mo_adgroup_daily",  "kakao_mo_adgroup",  "kakao_mo_campaign",  "advkey",      "cid",        "cname",        false),
        "Nda",    new MediaConfig("naver_gfa_adgroup_daily", "naver_gfa_adgroup", "naver_gfa_campaign", "daily_advid", "cid",        "cname",        false),
        "google", new MediaConfig("google_adgroup_daily",    "google_adgroup",    "google_campaign",    "daily_advid", "cid",        "cname",        false)
    );

    public Map<String, Object> getAdgroupReport(String userId, String md, String fromdate, String todate,
                                                 String campaignId, String sort, int start, int display) {
        AccountDto acc = accountMapper.selectByUserId(userId);
        if (acc == null) return fail("1009", "계정을 확인해 주세요.");

        MediaConfig cfg = MD_MAP.getOrDefault(md != null ? md : "N", MD_MAP.get("N"));
        String advid = getAdvid(acc, md);
        if (advid == null || advid.isBlank()) return noData();

        // 1. adgroup 일별 집계
        List<Map<String, Object>> stats = mongoService.aggregateByAdgroup(
            advid, fromdate, todate, cfg.dailyCol(), cfg.advField(), campaignId);
        if (stats.isEmpty()) return noData();

        // 2. adgroup 마스터 (gname, onoff, cid)
        List<Document> masterDocs = mongoService.findCampaigns(advid, cfg.masterCol());
        Map<String, Document> masterMap = new HashMap<>();
        for (Document d : masterDocs) {
            String gid = d.getString("gid");
            if (gid != null) masterMap.put(gid, d);
        }

        // 3. 캠페인 마스터 (campaign_name)
        List<Document> campDocs = mongoService.findCampaigns(advid, cfg.campCol());
        Map<String, Document> campMap = new HashMap<>();
        for (Document d : campDocs) {
            String cid = d.getString(cfg.campIdField());
            if (cid != null) campMap.put(cid, d);
        }

        // 4. 행 빌드
        List<Map<String, Object>> groups = new ArrayList<>();
        for (Map<String, Object> stat : stats) {
            String agid = (String) stat.get("adgroup_id");
            String cid  = (String) stat.get("campaign_id");
            Document master = masterMap.get(agid);
            Document camp   = campMap.get(cid);

            double im  = toDouble(stat, "im"),  clk = toDouble(stat, "clk"),
                   cst = toDouble(stat, "cst"), cv  = toDouble(stat, "cv"),
                   cr  = toDouble(stat, "cr");

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("campaign_id",            cid  != null ? cid  : "");
            row.put("campaign_name",          camp != null ? camp.getString(cfg.campNameField()) : "");
            row.put("campaign_type",          "");
            row.put("campaign_status",        "");
            row.put("campaign_status_reason", "");
            row.put("adgroup_id",             agid   != null ? agid : "");
            row.put("adgroup_name",           master != null ? master.getString("gname") : "");
            row.put("adgroup_type",           "");
            String regtm = master != null ? master.getString("regtm") : null;
            row.put("adgroup_regtm",          regtm != null ? regtm : "");
            row.put("adgroup_editm",          "");
            row.put("adgroup_status",         master != null ? getOnoff(master, cfg.naverOnoff()) : "");
            row.put("adgroup_status_reason",  "");
            row.put("im",  Math.round(im));  row.put("clk", Math.round(clk));
            row.put("cst", Math.round(cst)); row.put("cv",  Math.round(cv)); row.put("cr", Math.round(cr));
            row.putAll(calcMetrics(im, clk, cst, cv, cr));
            groups.add(row);
        }

        // 5. 정렬
        sortGroups(groups, sort);

        // 6. 페이지네이션
        int total   = groups.size();
        int fromIdx = start * display;
        int toIdx   = Math.min(fromIdx + display, total);
        List<Map<String, Object>> paged = (fromIdx < total) ? groups.subList(fromIdx, toIdx) : Collections.emptyList();

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("result",      "success");
        res.put("status",      "200");
        res.put("data",        Map.of("groups", paged));
        res.put("resultcount", 0);
        res.put("totalcount",  total);
        return res;
    }

    // ─── 헬퍼 ───────────────────────────────────────────────────────────────

    private String getAdvid(AccountDto acc, String md) {
        return switch (md != null ? md : "N") {
            case "D"      -> acc.getAccountKakaosa();
            case "K"      -> acc.getAccountKakaomoment();
            case "Nda"    -> acc.getAccountGfa();
            case "google" -> acc.getAccountGoogle();
            default       -> acc.getAccountNaverCustomer();
        };
    }

    private String getOnoff(Document d, boolean naverOnoff) {
        Object v = d.get("onoff");
        if (v == null) return "";
        if (v instanceof Integer i) {
            // naverOnoff=true: Naver SA parseInt("ON")→0, 0=on
            // naverOnoff=false: 표준(GFA activated→1, Kakao, Google), 1=on
            return naverOnoff ? (i == 0 ? "on" : "off") : (i == 1 ? "on" : "off");
        }
        if (v instanceof String s) return s.toLowerCase();
        return String.valueOf(v);
    }

    private void sortGroups(List<Map<String, Object>> groups, String sort) {
        String s = (sort != null) ? sort.trim() : "";
        String field;
        boolean desc;
        if (s.length() >= 2) {
            field = s.substring(0, s.length() - 1);
            desc  = s.charAt(s.length() - 1) == 'd';
        } else {
            field = "cst";
            desc  = true;
        }
        String f = field;
        groups.sort((x, y) -> {
            Object ox = x.get(f), oy = y.get(f);
            double dx = (ox instanceof Number n) ? n.doubleValue() : 0.0;
            double dy = (oy instanceof Number n) ? n.doubleValue() : 0.0;
            return desc ? Double.compare(dy, dx) : Double.compare(dx, dy);
        });
    }

    private Map<String, Object> calcMetrics(double im, double clk, double cst, double cv, double cr) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ctr",  (im > 0 && clk > 0)  ? fmt(clk / im  * 100) : 0);
        m.put("cpc",  (cst > 0 && clk > 0) ? fmt(cst / clk)       : 0);
        m.put("cpa",  (cst > 0 && cv > 0)  ? fmt(cst / cv)        : 0);
        m.put("cvr",  (cv > 0 && clk > 0)  ? fmt(cv  / clk * 100) : 0);
        m.put("roas", (cr > 0 && cst > 0)  ? fmt(cr  / cst * 100) : 0);
        return m;
    }

    private double fmt(double v) { return Math.round(v * 100.0) / 100.0; }

    private double toDouble(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return (v instanceof Number n) ? n.doubleValue() : 0.0;
    }

    private double toDouble2(Map<String, Object> m, String k) {
        Object v = m.get(k);
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof String s) { try { return Double.parseDouble(s); } catch (Exception e) { return 0.0; } }
        return 0.0;
    }

    private long toLong(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return (v instanceof Number n) ? n.longValue() : 0L;
    }

    private Map<String, Object> fail(String status, String msg) {
        return Map.of("result", "failed", "status", status, "errormessage", msg);
    }

    private Map<String, Object> noData() {
        Map<String, Object> r = new java.util.LinkedHashMap<>();
        r.put("result", "success"); r.put("status", "1004");
        r.put("errormessage", "검색 결과가 없습니다.");
        r.put("data", Map.of("groups", Collections.emptyList()));
        r.put("totalcount", 0);
        return r;
    }
}
