package com.h3.h3_java.api.service.analysis;

import com.h3.h3_java.api.dto.AccountDto;
import com.h3.h3_java.raw.mongo.AccountMongoService;
import com.h3.h3_java.raw.mongo.DashboardMongoService;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DiagnosisReportService {

    private final AccountMongoService accountMongo;
    private final DashboardMongoService mongoService;

    record MediaCfg(
        String campCol, String agCol, String kwCol, String adCol, String advField,
        String agMasterCol, String campMasterCol,
        String campIdFieldInMaster, String campNameFieldInMaster
    ) {}

    private static final Map<String, MediaCfg> MEDIA_MAP = Map.of(
        "naver",   new MediaCfg(
            "naver_campaign_daily",     "naver_adgroup_daily",     "naver_keyword_daily",    "naver_ad_daily",     "daily_advid",
            "naver_adgroup",     "naver_campaign",     "campaignid", "campaignname"),
        "naverda", new MediaCfg(
            "naver_gfa_campaign_daily", "naver_gfa_adgroup_daily", null,                     "naver_gfa_ad_daily", "daily_advid",
            "naver_gfa_adgroup", "naver_gfa_campaign", "cid",        "cname"),
        "kakaosa", new MediaCfg(
            "kakao_sa_campaign_daily",  "kakao_sa_adgroup_daily",  "kakao_sa_keyword_daily", "kakao_sa_ad_daily",  "advkey",
            "kakao_sa_adgroup",  "kakao_sa_campaign",  "cid",        "cname"),
        "kakaomo", new MediaCfg(
            "kakao_mo_campaign_daily",  "kakao_mo_adgroup_daily",  null,                     "kakao_mo_ad_daily",  "advkey",
            "kakao_mo_adgroup",  "kakao_mo_campaign",  "cid",        "cname"),
        "google",  new MediaCfg(
            "google_campaign_daily",    "google_adgroup_daily",    "google_keyword_daily",   "google_ad_daily",    "daily_advid",
            "google_adgroup",    "google_campaign",    "cid",        "cname")
    );

    public Map<String, Object> getDiagnosisReport(String userId, String fromdate, String todate,
                                                    String comparefromdate, String comparetodate,
                                                    String media, String campaignId) {
        AccountDto acc = accountMongo.findAccountDtoByUserId(userId);
        if (acc == null) return fail("1009", "계정 없음");

        MediaCfg cfg = MEDIA_MAP.get(media);
        if (cfg == null) return fail("1001", "지원하지 않는 매체입니다.");

        String advid = getAdvid(acc, media);
        if (advid == null || advid.isBlank()) return noData(media);

        String cfrom = (comparefromdate != null && !comparefromdate.isEmpty()) ? comparefromdate : fromdate;
        String cto   = (comparetodate   != null && !comparetodate.isEmpty())   ? comparetodate   : todate;

        // 1. Campaign count
        List<Map<String, Object>> campStats = mongoService.aggregateByCampaign(
            advid, fromdate, todate, cfg.campCol(), cfg.advField());
        if (campaignId != null && !campaignId.isBlank()) {
            campStats = campStats.stream()
                .filter(r -> campaignId.equals(r.get("campaign_id")))
                .collect(Collectors.toList());
        }
        int campCount = campStats.size();

        // 2. Adgroup count
        String agFilterId = (campaignId != null && !campaignId.isBlank()) ? campaignId : null;
        List<Map<String, Object>> agStats = mongoService.aggregateByAdgroup(
            advid, fromdate, todate, cfg.agCol(), cfg.advField(), agFilterId);
        int agCount = agStats.size();

        // 3. Keyword count
        int kwCount = 0;
        List<Map<String, Object>> kwStats = new ArrayList<>();
        if (cfg.kwCol() != null) {
            kwStats = mongoService.aggregateByKeyword(advid, fromdate, todate, cfg.kwCol(), cfg.advField());
            if (campaignId != null && !campaignId.isBlank()) {
                kwStats = kwStats.stream()
                    .filter(r -> campaignId.equals(r.get("campaign_id")))
                    .collect(Collectors.toList());
            }
            kwCount = kwStats.size();
        }

        // 4. Creative count
        List<Map<String, Object>> adStats = mongoService.aggregateByAd(
            advid, fromdate, todate, cfg.adCol(), cfg.advField());
        if (campaignId != null && !campaignId.isBlank()) {
            adStats = adStats.stream()
                .filter(r -> campaignId.equals(r.get("campaign_id")))
                .collect(Collectors.toList());
        }
        int adCount = adStats.size();

        // 5. Determine level
        String level;
        boolean fallback;
        if (campCount > 1) {
            level = "campaign";
            fallback = false;
        } else if (agCount >= 2) {
            level = "adgroup";
            fallback = true;
        } else if (kwCount >= 2) {
            level = "keyword";
            fallback = true;
        } else if (adCount >= 2) {
            level = "creative";
            fallback = true;
        } else {
            level = "none";
            fallback = true;
        }

        String levelLabel = getLevelLabel(level);
        String message    = buildMessage(level);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("campaign_count", campCount);
        summary.put("adgroup_count",  agCount);
        summary.put("keyword_count",  kwCount);
        summary.put("creative_count", adCount);

        // 6. Build items by level
        List<Map<String, Object>> mainItems;
        if ("none".equals(level)) {
            mainItems = new ArrayList<>();
        } else if ("campaign".equals(level)) {
            mainItems = buildCampaignItems(campStats, advid, cfrom, cto, cfg);
        } else if ("adgroup".equals(level)) {
            mainItems = buildAdgroupItems(agStats, advid, cfrom, cto, cfg, agFilterId);
        } else if ("keyword".equals(level)) {
            mainItems = buildKeywordItems(kwStats, advid, cfrom, cto, cfg, campaignId);
        } else {
            mainItems = buildCreativeItems(adStats, advid, cfrom, cto, cfg, campaignId);
        }

        // 7. Build TOP3 lists
        List<Map<String, Object>> costTop3 = mainItems.stream()
            .filter(i -> {
                Map<?, ?> d = (Map<?, ?>) i.get("diff");
                return d != null && td(d, "cst") > 0;
            })
            .sorted((a, b) -> Double.compare(td((Map<?, ?>) b.get("diff"), "cst"), td((Map<?, ?>) a.get("diff"), "cst")))
            .limit(3)
            .collect(Collectors.toList());

        List<Map<String, Object>> convTop3 = mainItems.stream()
            .filter(i -> {
                Map<?, ?> d = (Map<?, ?>) i.get("diff");
                Map<?, ?> cur = (Map<?, ?>) i.get("current");
                Map<?, ?> comp = (Map<?, ?>) i.get("compare");
                return d != null && td(d, "cv") < 0
                    && cur != null && comp != null
                    && td(cur, "cv") < td(comp, "cv");
            })
            .sorted((a, b) -> Double.compare(td((Map<?, ?>) a.get("diff"), "cv"), td((Map<?, ?>) b.get("diff"), "cv")))
            .limit(3)
            .collect(Collectors.toList());

        List<Map<String, Object>> roasTop3 = mainItems.stream()
            .filter(i -> {
                Map<?, ?> d = (Map<?, ?>) i.get("diff");
                Map<?, ?> cur = (Map<?, ?>) i.get("current");
                Map<?, ?> comp = (Map<?, ?>) i.get("compare");
                return d != null && td(d, "purchase_roas") < 0
                    && cur != null && td(cur, "purchase_roas") > 0
                    && comp != null && td(comp, "purchase_roas") > 0;
            })
            .sorted((a, b) -> Double.compare(
                td((Map<?, ?>) a.get("diff"), "purchase_roas"),
                td((Map<?, ?>) b.get("diff"), "purchase_roas")))
            .limit(3)
            .collect(Collectors.toList());

        // 8. Sort main by |diff.cst| DESC (impact score)
        List<Map<String, Object>> sortedMain = new ArrayList<>(mainItems);
        sortedMain.sort((a, b) -> Double.compare(
            Math.abs(td((Map<?, ?>) b.get("diff"), "cst")),
            Math.abs(td((Map<?, ?>) a.get("diff"), "cst"))));

        addRanks(sortedMain);
        addRanks(costTop3);
        addRanks(convTop3);
        addRanks(roasTop3);

        // 9. Assemble response
        Map<String, Object> mainTab = new LinkedHashMap<>();
        mainTab.put("title", "주요 " + levelLabel);
        mainTab.put("items", sortedMain);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("media",       media);
        data.put("level",       level);
        data.put("level_label", levelLabel);
        data.put("fallback",    fallback);
        data.put("message",     message);
        data.put("summary",     summary);
        data.put("tabs",        Map.of("main", mainTab));
        data.put("cost_increase",         Map.of("title", "광고비 증가 TOP3",            "items", costTop3));
        data.put("conversion_decrease",   Map.of("title", "전환수 감소 TOP3",            "items", convTop3));
        data.put("purchase_roas_decrease",Map.of("title", "구매완료광고수익률 하락 TOP3", "items", roasTop3));

        return Map.of("result", "success", "status", "200", "data", data);
    }

    // ─── Level item builders ──────────────────────────────────────────────────

    private List<Map<String, Object>> buildCampaignItems(List<Map<String, Object>> curList,
                                                          String advid, String cfrom, String cto,
                                                          MediaCfg cfg) {
        List<Map<String, Object>> compList = mongoService.aggregateByCampaign(advid, cfrom, cto, cfg.campCol(), cfg.advField());
        Map<String, Map<String, Object>> compMap = toMap(compList, "campaign_id");

        List<Document> masters = mongoService.findCampaigns(advid, cfg.campMasterCol());
        Map<String, String> nameMap = buildNameMap(masters, cfg.campIdFieldInMaster(), cfg.campNameFieldInMaster());

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> cur : curList) {
            String cid  = (String) cur.get("campaign_id");
            String name = nameMap.getOrDefault(cid, cid != null ? cid : "");
            result.add(buildItem(cid, name, null, cur, compMap.getOrDefault(cid, Map.of())));
        }
        return result;
    }

    private List<Map<String, Object>> buildAdgroupItems(List<Map<String, Object>> curList,
                                                         String advid, String cfrom, String cto,
                                                         MediaCfg cfg, String campaignId) {
        List<Map<String, Object>> compList = mongoService.aggregateByAdgroup(
            advid, cfrom, cto, cfg.agCol(), cfg.advField(), campaignId);
        Map<String, Map<String, Object>> compMap = toMap(compList, "adgroup_id");

        List<Document> agMasters   = mongoService.findCampaigns(advid, cfg.agMasterCol());
        List<Document> campMasters = mongoService.findCampaigns(advid, cfg.campMasterCol());
        Map<String, String> agNameMap   = buildNameMap(agMasters,   "gid", "gname");
        Map<String, String> campNameMap = buildNameMap(campMasters, cfg.campIdFieldInMaster(), cfg.campNameFieldInMaster());

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> cur : curList) {
            String agid  = (String) cur.get("adgroup_id");
            String cid   = (String) cur.get("campaign_id");
            String name  = agNameMap.getOrDefault(agid, agid != null ? agid : "");
            String pname = campNameMap.getOrDefault(cid, cid != null ? cid : "");
            result.add(buildItem(agid, name, pname, cur, compMap.getOrDefault(agid, Map.of())));
        }
        return result;
    }

    private List<Map<String, Object>> buildKeywordItems(List<Map<String, Object>> curList,
                                                         String advid, String cfrom, String cto,
                                                         MediaCfg cfg, String campaignId) {
        List<Map<String, Object>> compList = mongoService.aggregateByKeyword(advid, cfrom, cto, cfg.kwCol(), cfg.advField());
        if (campaignId != null && !campaignId.isBlank()) {
            compList = compList.stream()
                .filter(r -> campaignId.equals(r.get("campaign_id")))
                .collect(Collectors.toList());
        }
        Map<String, Map<String, Object>> compMap = toMap(compList, "keyword_id");

        List<Document> campMasters = mongoService.findCampaigns(advid, cfg.campMasterCol());
        Map<String, String> campNameMap = buildNameMap(campMasters, cfg.campIdFieldInMaster(), cfg.campNameFieldInMaster());

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> cur : curList) {
            String kwid  = (String) cur.get("keyword_id");
            String kname = (String) cur.get("kname");
            String cid   = (String) cur.get("campaign_id");
            String pname = campNameMap.getOrDefault(cid, cid != null ? cid : "");
            String name  = (kname != null && !kname.isBlank()) ? kname : (kwid != null ? kwid : "");
            result.add(buildItem(kwid, name, pname, cur, compMap.getOrDefault(kwid, Map.of())));
        }
        return result;
    }

    private List<Map<String, Object>> buildCreativeItems(List<Map<String, Object>> curList,
                                                          String advid, String cfrom, String cto,
                                                          MediaCfg cfg, String campaignId) {
        List<Map<String, Object>> compList = mongoService.aggregateByAd(advid, cfrom, cto, cfg.adCol(), cfg.advField());
        if (campaignId != null && !campaignId.isBlank()) {
            compList = compList.stream()
                .filter(r -> campaignId.equals(r.get("campaign_id")))
                .collect(Collectors.toList());
        }
        Map<String, Map<String, Object>> compMap = toMap(compList, "ad_id");

        List<Document> campMasters = mongoService.findCampaigns(advid, cfg.campMasterCol());
        Map<String, String> campNameMap = buildNameMap(campMasters, cfg.campIdFieldInMaster(), cfg.campNameFieldInMaster());

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> cur : curList) {
            String aid   = (String) cur.get("ad_id");
            String cid   = (String) cur.get("campaign_id");
            String pname = campNameMap.getOrDefault(cid, cid != null ? cid : "");
            result.add(buildItem(aid, aid != null ? aid : "", pname, cur, compMap.getOrDefault(aid, Map.of())));
        }
        return result;
    }

    // ─── Item builder ─────────────────────────────────────────────────────────

    private Map<String, Object> buildItem(String id, String name, String parentName,
                                          Map<String, Object> cur, Map<?, ?> comp) {
        double cIm = td(cur,"im"), cClk = td(cur,"clk"), cCst = td(cur,"cst"), cCv = td(cur,"cv"), cCr = td(cur,"cr");
        double pIm = td(comp,"im"), pClk = td(comp,"clk"), pCst = td(comp,"cst"), pCv = td(comp,"cv"), pCr = td(comp,"cr");

        Map<String, Object> current = buildMetrics(cIm, cClk, cCst, cCv, cCr);
        Map<String, Object> compare = buildMetrics(pIm, pClk, pCst, pCv, pCr);

        Map<String, Object> diff = new LinkedHashMap<>();
        Map<String, Object> per  = new LinkedHashMap<>();
        Set<String> ppKeys = Set.of("ctr","cpc","cpa","cvr","roas","purchase_roas");
        for (String k : new String[]{"im","clk","cst","cv","cr","ctr","cpc","cpa","cvr","roas","purchase_roas"}) {
            double c = td(current, k), p = td(compare, k);
            if (ppKeys.contains(k)) {
                diff.put(k, fmt(c - p));
                per.put(k,  p > 0 ? fmt((c - p) / p * 100) : 0);
            } else {
                diff.put(k, (long) Math.round(c - p));
                per.put(k,  p > 0 ? fmt((c - p) / p * 100) : 0);
            }
        }

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id",          id          != null ? id          : "");
        item.put("name",        name        != null ? name        : "");
        item.put("parent_name", parentName  != null ? parentName  : "");
        item.put("current",  current);
        item.put("compare",  compare);
        item.put("diff",     diff);
        item.put("per",      per);
        return item;
    }

    private Map<String, Object> buildMetrics(double im, double clk, double cst, double cv, double cr) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("im",  (long) im);
        m.put("clk", (long) clk);
        m.put("cst", (long) cst);
        m.put("cv",  (long) cv);
        m.put("cr",  (long) cr);
        m.put("ctr",  (clk > 0 && im  > 0) ? fmt(clk / im  * 100) : 0);
        m.put("cpc",  (cst > 0 && clk > 0) ? fmt(cst / clk)       : 0);
        m.put("cpa",  (cst > 0 && cv  > 0) ? fmt(cst / cv)        : 0);
        m.put("cvr",  (cv  > 0 && clk > 0) ? fmt(cv  / clk * 100) : 0);
        m.put("roas", (cr  > 0 && cst > 0) ? fmt(cr  / cst * 100) : 0);
        m.put("purchase_roas", 0);
        return m;
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private void addRanks(List<Map<String, Object>> items) {
        for (int i = 0; i < items.size(); i++) items.get(i).put("rank", i + 1);
    }

    private Map<String, Map<String, Object>> toMap(List<Map<String, Object>> list, String idField) {
        Map<String, Map<String, Object>> map = new HashMap<>();
        for (Map<String, Object> r : list) {
            String id = (String) r.get(idField);
            if (id != null) map.put(id, r);
        }
        return map;
    }

    private Map<String, String> buildNameMap(List<Document> docs, String idField, String nameField) {
        Map<String, String> map = new HashMap<>();
        for (Document d : docs) {
            String id = d.getString(idField);
            if (id != null) map.put(id, d.getString(nameField));
        }
        return map;
    }

    private String getLevelLabel(String level) {
        return switch (level) {
            case "campaign" -> "캠페인";
            case "adgroup"  -> "광고그룹";
            case "keyword"  -> "키워드";
            case "creative" -> "소재";
            default -> "";
        };
    }

    private String buildMessage(String level) {
        return switch (level) {
            case "campaign" -> "캠페인이 여러 개입니다.";
            case "adgroup"  -> "캠페인이 1개라 광고그룹을 기준으로 성과 변화를 분석했습니다.";
            case "keyword"  -> "광고그룹이 1개라 키워드를 기준으로 성과 변화를 분석했습니다.";
            case "creative" -> "광고그룹이 1개라 소재를 기준으로 성과 변화를 분석했습니다.";
            default -> "비교 가능한 항목이 부족합니다.";
        };
    }

    private String getAdvid(AccountDto acc, String media) {
        return switch (media) {
            case "naver"   -> acc.getAccountNaverCustomer();
            case "naverda" -> acc.getAccountGfa();
            case "kakaosa" -> acc.getAccountKakaosa();
            case "kakaomo" -> acc.getAccountKakaomoment();
            case "google"  -> acc.getAccountGoogle();
            default -> null;
        };
    }

    private double td(Map<?, ?> m, String key) {
        if (m == null) return 0;
        Object v = m.get(key);
        return (v instanceof Number) ? ((Number) v).doubleValue() : 0;
    }

    private double fmt(double v) { return Math.round(v * 100.0) / 100.0; }

    private Map<String, Object> fail(String status, String msg) {
        return Map.of("result", "failed", "status", status, "errormessage", msg);
    }

    private Map<String, Object> noData(String media) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("media",       media);
        data.put("level",       "none");
        data.put("level_label", "");
        data.put("fallback",    true);
        data.put("message",     "해당 매체 계정이 없습니다.");
        data.put("summary",     Map.of("campaign_count",0,"adgroup_count",0,"keyword_count",0,"creative_count",0));
        data.put("tabs",        Map.of("main", Map.of("title","","items", new ArrayList<>())));
        data.put("cost_increase",          Map.of("title","광고비 증가 TOP3",            "items", new ArrayList<>()));
        data.put("conversion_decrease",    Map.of("title","전환수 감소 TOP3",            "items", new ArrayList<>()));
        data.put("purchase_roas_decrease", Map.of("title","구매완료광고수익률 하락 TOP3","items", new ArrayList<>()));
        return Map.of("result","success","status","200","data",data);
    }
}
