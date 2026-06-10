package com.h3.h3_java.api.service.analysis;

import com.h3.h3_java.api.dto.AccountDto;
import com.h3.h3_java.api.mapper.AccountMapper;
import com.h3.h3_java.raw.mongo.NaverStatMongoService;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ShoppingReportService {

    private final AccountMapper accountMapper;
    private final NaverStatMongoService naverStatMongoService;

    public Map<String, Object> getShoppingReport(
            String userId, String fromdate, String todate,
            String kpi, String adpid, String adid,
            String sort, int start, int display) {

        AccountDto acc = accountMapper.selectByUserId(userId);
        if (acc == null) return fail("1009", "계정을 확인해 주세요.");

        String advkey = acc.getAccountNaverCustomer();
        if (advkey == null || advkey.isBlank()) return noData();

        // pid/adid 기준 키워드 조회: 키워드 단위 데이터 없음 → 빈 응답
        if (!adpid.isBlank() || !adid.isBlank()) {
            return emptyKeywordResponse();
        }

        if (!kpi.isBlank()) {
            return getShoppingTop(advkey, fromdate, todate, kpi);
        }
        return getShopping(advkey, fromdate, todate, sort, start, display);
    }

    // ─── 전체 목록 (pid 기준 그룹핑) ────────────────────────────────────────

    private Map<String, Object> getShopping(String advkey, String from, String to,
                                             String sort, int start, int display) {
        List<Map<String, Object>> stats = naverStatMongoService.aggregateShoppingByAdId(advkey, from, to);
        if (stats.isEmpty()) return noData();

        Map<String, Document> productMap     = buildProductMap(advkey);
        Map<String, String>   campaignNameMap = buildCampaignNameMap(advkey);
        Map<String, String>   adgroupNameMap  = buildAdgroupNameMap(advkey);

        Map<String, Map<String, Object>> pidGroupMap = new LinkedHashMap<>();

        for (Map<String, Object> stat : stats) {
            String adId  = (String) stat.get("ad_id");
            String adPid = (String) stat.get("ad_pid");

            if (adPid == null || adPid.isBlank()) {
                Document p = productMap.get(adId);
                if (p != null) adPid = p.getString("pid");
            }
            if (adPid == null || adPid.isBlank()) continue;

            Document product   = productMap.get(adId);
            String campaignId  = (String) stat.get("campaign_id");
            String adgroupId   = (String) stat.get("adgroup_id");
            double im  = toDoubleMap(stat,"im"),  clk = toDoubleMap(stat,"clk");
            double cst = toDoubleMap(stat,"cst"), cv  = toDoubleMap(stat,"cv");
            double cr  = toDoubleMap(stat,"cr");

            Map<String, Object> target = new LinkedHashMap<>();
            target.put("campaign_id",    s(campaignId));
            target.put("campaign_name",  campaignNameMap.getOrDefault(campaignId, ""));
            target.put("adgroup_id",     s(adgroupId));
            target.put("adgroup_name",   adgroupNameMap.getOrDefault(adgroupId, ""));
            target.put("ad_id",          s(adId));
            target.put("ad_pid",         adPid);
            target.put("ad_pidofmall",   product != null ? str(product, "pidofmall") : "");
            target.put("ad_pimageurl",   product != null ? str(product, "pimageurl") : "");
            target.put("ad_pname",       product != null ? str(product, "pname") : "");
            target.put("ad_productname", product != null ? str(product, "productname") : "");
            target.put("im",  (long) im);  target.put("clk", (long) clk); target.put("cst", (long) cst);
            target.put("cv",  (long) cv);  target.put("cr",  (long) cr);
            target.putAll(calcMetrics(im, clk, cst, cv, cr));

            final String finalAdPid  = adPid;
            final Document finalProd = product;
            pidGroupMap.computeIfAbsent(adPid, k -> {
                Map<String, Object> g = new LinkedHashMap<>();
                g.put("ad_pid",         finalAdPid);
                g.put("ad_productname", finalProd != null ? str(finalProd, "pname") : "");
                g.put("im", 0L); g.put("clk", 0L); g.put("cst", 0L);
                g.put("cv", 0L); g.put("cr",  0L);
                g.put("targets", new ArrayList<Map<String, Object>>());
                return g;
            });

            Map<String, Object> group = pidGroupMap.get(adPid);
            group.put("im",  (long)(toDoubleObj(group.get("im"))  + im));
            group.put("clk", (long)(toDoubleObj(group.get("clk")) + clk));
            group.put("cst", (long)(toDoubleObj(group.get("cst")) + cst));
            group.put("cv",  (long)(toDoubleObj(group.get("cv"))  + cv));
            group.put("cr",  (long)(toDoubleObj(group.get("cr"))  + cr));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> targets = (List<Map<String, Object>>) group.get("targets");
            targets.add(target);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> group : pidGroupMap.values()) {
            double gIm  = toDoubleObj(group.get("im")),  gClk = toDoubleObj(group.get("clk"));
            double gCst = toDoubleObj(group.get("cst")), gCv  = toDoubleObj(group.get("cv"));
            double gCr  = toDoubleObj(group.get("cr"));
            group.putAll(calcMetrics(gIm, gClk, gCst, gCv, gCr));
            result.add(group);
        }

        sortRows(result, sort);

        int total   = result.size();
        int fromIdx = start * display;
        int toIdx   = Math.min(fromIdx + display, total);
        List<Map<String, Object>> paged = fromIdx < total ? result.subList(fromIdx, toIdx) : Collections.emptyList();

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("result", "success"); res.put("status", "200");
        res.put("data",  Map.of("ads", paged, "topads", ""));
        res.put("resultcount", 0); res.put("totalcount", total);
        return res;
    }

    // ─── KPI별 Top 2 ─────────────────────────────────────────────────────────

    private Map<String, Object> getShoppingTop(String advkey, String from, String to, String kpi) {
        String[] kpis = kpi.split(",");
        Map<String, Object> topads = new LinkedHashMap<>();

        List<Map<String, Object>> stats = naverStatMongoService.aggregateShoppingByAdId(advkey, from, to);
        if (stats.isEmpty()) {
            for (String k : kpis) topads.put(k.trim(), Collections.emptyList());
            return buildTopResponse(topads);
        }

        Map<String, Document> productMap     = buildProductMap(advkey);
        Map<String, String>   campaignNameMap = buildCampaignNameMap(advkey);
        Map<String, String>   adgroupNameMap  = buildAdgroupNameMap(advkey);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> stat : stats) {
            String adId  = (String) stat.get("ad_id");
            String adPid = (String) stat.get("ad_pid");
            if (adPid == null || adPid.isBlank()) {
                Document p = productMap.get(adId);
                if (p != null) adPid = p.getString("pid");
            }
            if (adPid == null || adPid.isBlank()) continue;

            Document product  = productMap.get(adId);
            String campaignId = (String) stat.get("campaign_id");
            String adgroupId  = (String) stat.get("adgroup_id");
            double im  = toDoubleMap(stat,"im"),  clk = toDoubleMap(stat,"clk");
            double cst = toDoubleMap(stat,"cst"), cv  = toDoubleMap(stat,"cv");
            double cr  = toDoubleMap(stat,"cr");

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("campaign_id",    s(campaignId));
            row.put("campaign_name",  campaignNameMap.getOrDefault(campaignId, ""));
            row.put("adgroup_id",     s(adgroupId));
            row.put("adgroup_name",   adgroupNameMap.getOrDefault(adgroupId, ""));
            row.put("ad_id",          s(adId));
            row.put("ad_pid",         adPid);
            row.put("ad_pidofmall",   product != null ? str(product, "pidofmall") : "");
            row.put("ad_pimageurl",   product != null ? str(product, "pimageurl") : "");
            row.put("ad_pname",       product != null ? str(product, "pname") : "");
            row.put("ad_productname", product != null ? str(product, "productname") : "");
            row.put("im",  (long) im);  row.put("clk", (long) clk); row.put("cst", (long) cst);
            row.put("cv",  (long) cv);  row.put("cr",  (long) cr);
            row.putAll(calcMetrics(im, clk, cst, cv, cr));
            rows.add(row);
        }

        for (String k : kpis) {
            String t = k.trim();
            List<Map<String, Object>> sorted = new ArrayList<>(rows);
            sorted.sort((a, b) -> Double.compare(toDoubleObj(b.get(t)), toDoubleObj(a.get(t))));
            topads.put(t, sorted.size() > 2 ? new ArrayList<>(sorted.subList(0, 2)) : sorted);
        }
        return buildTopResponse(topads);
    }

    // ─── 헬퍼 ─────────────────────────────────────────────────────────────────

    private Map<String, Object> buildTopResponse(Map<String, Object> topads) {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("result", "success"); res.put("status", "200");
        res.put("data",  Map.of("ads", "", "topads", topads));
        res.put("resultcount", 0); res.put("totalcount", 0);
        return res;
    }

    private Map<String, Object> emptyKeywordResponse() {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("result", "success"); res.put("status", "200");
        res.put("data",  Map.of("ads", Collections.emptyList(), "topads", ""));
        res.put("resultcount", 0); res.put("totalcount", 0);
        return res;
    }

    private Map<String, Document> buildProductMap(String advkey) {
        return naverStatMongoService.findShoppingProducts(advkey).stream()
            .filter(d -> d.getString("adid") != null)
            .collect(Collectors.toMap(d -> d.getString("adid"), d -> d, (a, b) -> a));
    }

    private Map<String, String> buildCampaignNameMap(String advkey) {
        return naverStatMongoService.findNaverCampaignDocs(advkey).stream()
            .filter(d -> d.getString("campaignid") != null)
            .collect(Collectors.toMap(
                d -> d.getString("campaignid"),
                d -> d.getString("campaignname") != null ? d.getString("campaignname") : "",
                (a, b) -> a));
    }

    private Map<String, String> buildAdgroupNameMap(String advkey) {
        return naverStatMongoService.findNaverAdgroupDocs(advkey).stream()
            .filter(d -> d.getString("gid") != null)
            .collect(Collectors.toMap(
                d -> d.getString("gid"),
                d -> d.getString("gname") != null ? d.getString("gname") : "",
                (a, b) -> a));
    }

    private void sortRows(List<Map<String, Object>> rows, String sort) {
        String s = sort != null ? sort.trim() : "";
        String field; boolean desc;
        if (s.length() >= 2) { field = s.substring(0, s.length()-1); desc = s.charAt(s.length()-1) == 'd'; }
        else                  { field = "cst"; desc = true; }
        String f = field;
        rows.sort((x, y) -> {
            double dx = toDoubleObj(x.get(f)), dy = toDoubleObj(y.get(f));
            return desc ? Double.compare(dy, dx) : Double.compare(dx, dy);
        });
    }

    private Map<String, Object> calcMetrics(double im, double clk, double cst, double cv, double cr) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ctr",  (im>0&&clk>0)  ? fmt(clk/im*100)  : 0);
        m.put("cpc",  (cst>0&&clk>0) ? fmt(cst/clk)     : 0);
        m.put("cpa",  (cst>0&&cv>0)  ? fmt(cst/cv)      : 0);
        m.put("cvr",  (cv>0&&clk>0)  ? fmt(cv/clk*100)  : 0);
        m.put("roas", (cr>0&&cst>0)  ? fmt(cr/cst*100)  : 0);
        return m;
    }

    private String str(Document d, String key) {
        if (d == null) return "";
        String v = d.getString(key);
        return v != null ? v : "";
    }
    private String s(String v)                            { return v != null ? v : ""; }
    private double toDoubleMap(Map<String, Object> m, String k) { Object v=m.get(k); return v instanceof Number n ? n.doubleValue() : 0.0; }
    private double toDoubleObj(Object v)                  { return v instanceof Number n ? n.doubleValue() : 0.0; }
    private double fmt(double v)                          { return Math.round(v * 100.0) / 100.0; }
    private Map<String, Object> fail(String status, String msg) { return Map.of("result","failed","status",status,"errormessage",msg); }
    private Map<String, Object> noData()                  { return Map.of("result","success","status","1004","errormessage","검색 결과가 없습니다."); }
}
