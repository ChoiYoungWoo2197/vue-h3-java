package com.h3.h3_java.api.controller;

import com.h3.h3_java.api.service.analysis.AdReportService;
import com.h3.h3_java.api.service.analysis.AdgroupAdReportService;
import com.h3.h3_java.api.service.analysis.AdgroupKeywordReportService;
import com.h3.h3_java.api.service.analysis.AdgroupReportService;
import com.h3.h3_java.api.service.analysis.AdgroupShoppingReportService;
import com.h3.h3_java.api.service.analysis.CampaignAdReportService;
import com.h3.h3_java.api.service.analysis.CampaignKeywordReportService;
import com.h3.h3_java.api.service.analysis.CampaignReportService;
import com.h3.h3_java.api.service.analysis.CampaignShoppingReportService;
import com.h3.h3_java.api.service.analysis.DiagnosisReportService;
import com.h3.h3_java.api.service.analysis.KeywordReportService;
import com.h3.h3_java.api.service.analysis.KeywordReReportService;
import com.h3.h3_java.api.service.analysis.MediaReportService;
import com.h3.h3_java.api.service.analysis.PeriodReportService;
import com.h3.h3_java.api.service.analysis.ShoppingReportService;
import com.h3.h3_java.api.service.analysis.TargetReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/v1/h3/app/analysis")
@RequiredArgsConstructor
public class AnalysisController {

    private final CampaignReportService          campaignReportService;
    private final DiagnosisReportService         diagnosisReportService;
    private final AdgroupReportService           adgroupReportService;
    private final KeywordReportService           keywordReportService;
    private final AdgroupKeywordReportService    adgroupKeywordReportService;
    private final CampaignKeywordReportService   campaignKeywordReportService;
    private final AdReportService                adReportService;
    private final AdgroupAdReportService         adgroupAdReportService;
    private final CampaignAdReportService        campaignAdReportService;
    private final MediaReportService             mediaReportService;
    private final KeywordReReportService         keywordReReportService;
    private final ShoppingReportService          shoppingReportService;
    private final AdgroupShoppingReportService   adgroupShoppingReportService;
    private final CampaignShoppingReportService  campaignShoppingReportService;
    private final PeriodReportService            periodReportService;
    private final TargetReportService            targetReportService;

    /** 진단 리포트 (캠페인 1개일 때 하위 단위 fallback) */
    @GetMapping("/diagnosisreport")
    public Map<String, Object> diagnosisReport(
            @RequestParam String userid,
            @RequestParam String fromdate,
            @RequestParam String todate,
            @RequestParam(required = false) String comparefromdate,
            @RequestParam(required = false) String comparetodate,
            @RequestParam(required = false, defaultValue = "naver") String media,
            @RequestParam(required = false, defaultValue = "") String campaignid) {
        return diagnosisReportService.getDiagnosisReport(
            userid, fromdate, todate, comparefromdate, comparetodate, media, campaignid);
    }

    /** 진단 상세 리포트 — 캠페인 1개일 때 프론트가 호출, campaignid 필수 */
    @GetMapping("/diagnosisdetailreport")
    public Map<String, Object> diagnosisDetailReport(
            @RequestParam String userid,
            @RequestParam String fromdate,
            @RequestParam String todate,
            @RequestParam(required = false) String comparefromdate,
            @RequestParam(required = false) String comparetodate,
            @RequestParam(required = false, defaultValue = "naver") String media,
            @RequestParam(required = false, defaultValue = "") String campaignid) {
        return diagnosisReportService.getDiagnosisDetailReport(
            userid, fromdate, todate, comparefromdate, comparetodate, media, campaignid);
    }

    /** 캠페인 리포트 */
    @GetMapping("/campaignreport")
    public Map<String, Object> campaignReport(
            @RequestParam String userid,
            @RequestParam String fromdate,
            @RequestParam String todate,
            @RequestParam(required = false) String comparefromdate,
            @RequestParam(required = false) String comparetodate) {
        return campaignReportService.getCampaignReport(userid, fromdate, todate, comparefromdate, comparetodate);
    }

    /** 키워드 리포트 */
    @GetMapping("/keywordreport")
    public Map<String, Object> keywordReport(
            @RequestParam String userid,
            @RequestParam String fromdate,
            @RequestParam String todate,
            @RequestParam(required = false) String comparefromdate,
            @RequestParam(required = false) String comparetodate,
            @RequestParam(required = false, defaultValue = "N") String md,
            @RequestParam(required = false, defaultValue = "") String kpi,
            @RequestParam(required = false, defaultValue = "cstd") String sort,
            @RequestParam(required = false, defaultValue = "0") int start,
            @RequestParam(required = false, defaultValue = "20") int display) {
        return keywordReportService.getKeywordReport(userid, md, fromdate, todate,
                comparefromdate, comparetodate, kpi, sort, start, display);
    }

    /** 소재 리포트 */
    @GetMapping("/adreport")
    public Map<String, Object> adReport(
            @RequestParam String userid,
            @RequestParam String fromdate,
            @RequestParam String todate,
            @RequestParam(required = false) String comparefromdate,
            @RequestParam(required = false) String comparetodate,
            @RequestParam(required = false, defaultValue = "N") String md,
            @RequestParam(required = false, defaultValue = "") String kpi,
            @RequestParam(required = false, defaultValue = "cstd") String sort,
            @RequestParam(required = false, defaultValue = "0") int start,
            @RequestParam(required = false, defaultValue = "20") int display,
            @RequestParam(required = false, defaultValue = "false") String base64) {
        return adReportService.getAdReport(userid, md, fromdate, todate,
                comparefromdate, comparetodate, kpi, sort, start, display, "true".equals(base64));
    }

    /** 키워드 추천 리포트 */
    @GetMapping("/keywordrereport")
    public Map<String, Object> keywordReReport(
            @RequestParam String userid,
            @RequestParam(required = false, defaultValue = "") String keywords,
            @RequestParam(required = false, defaultValue = "") String dkeywords) {
        return keywordReReportService.getKeywordReReport(userid, keywords, dkeywords);
    }

    /** 매체 리포트 */
    @GetMapping("/mediareport")
    public Map<String, Object> mediaReport(
            @RequestParam String userid,
            @RequestParam String fromdate,
            @RequestParam String todate,
            @RequestParam(required = false) String comparefromdate,
            @RequestParam(required = false) String comparetodate) {
        return mediaReportService.getMediaReport(userid, fromdate, todate, comparefromdate, comparetodate);
    }

    /** 쇼핑 리포트 */
    @GetMapping("/shoppingreport")
    public Map<String, Object> shoppingReport(
            @RequestParam String userid,
            @RequestParam String fromdate,
            @RequestParam String todate,
            @RequestParam(required = false) String comparefromdate,
            @RequestParam(required = false) String comparetodate,
            @RequestParam(required = false, defaultValue = "") String kpi,
            @RequestParam(required = false, defaultValue = "") String adpid,
            @RequestParam(required = false, defaultValue = "") String adid,
            @RequestParam(required = false, defaultValue = "cstd") String sort,
            @RequestParam(required = false, defaultValue = "0") int start,
            @RequestParam(required = false, defaultValue = "20") int display) {
        return shoppingReportService.getShoppingReport(userid, fromdate, todate, comparefromdate, comparetodate, kpi, adpid, adid, sort, start, display);
    }

    /** 광고그룹 쇼핑 리포트 */
    @GetMapping("/adgroupshoppingreport")
    public Map<String, Object> adgroupShoppingReport(
            @RequestParam String userid,
            @RequestParam String fromdate,
            @RequestParam String todate,
            @RequestParam(required = false) String comparefromdate,
            @RequestParam(required = false) String comparetodate,
            @RequestParam(required = false, defaultValue = "") String kpi,
            @RequestParam(required = false, defaultValue = "") String adgroupid) {
        return adgroupShoppingReportService.getAdgroupShoppingReport(
            userid, fromdate, todate, comparefromdate, comparetodate, kpi, adgroupid);
    }

    /** 캠페인 쇼핑 리포트 */
    @GetMapping("/campaignshoppingreport")
    public Map<String, Object> campaignShoppingReport(
            @RequestParam String userid,
            @RequestParam String fromdate,
            @RequestParam String todate,
            @RequestParam(required = false) String comparefromdate,
            @RequestParam(required = false) String comparetodate,
            @RequestParam(required = false, defaultValue = "") String kpi,
            @RequestParam(required = false, defaultValue = "") String campaignid) {
        return campaignShoppingReportService.getCampaignShoppingReport(
            userid, fromdate, todate, comparefromdate, comparetodate, kpi, campaignid);
    }

    /** 기간별 리포트 */
    @GetMapping("/periodreport")
    public Map<String, Object> periodReport(
            @RequestParam String userid,
            @RequestParam String fromdate,
            @RequestParam String todate,
            @RequestParam(required = false) String comparefromdate,
            @RequestParam(required = false) String comparetodate,
            @RequestParam(required = false, defaultValue = "day") String periodunit) {
        return periodReportService.getPeriodReport(userid, fromdate, todate,
                comparefromdate, comparetodate, periodunit);
    }

    /** 타겟 리포트 */
    @GetMapping("/targetreport")
    public Map<String, Object> targetReport(
            @RequestParam String userid,
            @RequestParam String fromdate,
            @RequestParam String todate,
            @RequestParam(required = false, defaultValue = "N") String md,
            @RequestParam(required = false, defaultValue = "campaign") String level,
            @RequestParam(required = false, defaultValue = "device") String target,
            @RequestParam(required = false, defaultValue = "") String id,
            @RequestParam(required = false, defaultValue = "0") int start,
            @RequestParam(required = false, defaultValue = "cstd") String sort,
            @RequestParam(required = false, defaultValue = "20") int display,
            @RequestParam(required = false, defaultValue = "-1") long totalcount) {
        return targetReportService.getTargetReport(userid, fromdate, todate, md, level, target, id, start, sort, display, totalcount);
    }

    /** 확장검색 키워드 리포트 (stub - 추후 구현) */
    @GetMapping("/expkeywordreport")
    public Map<String, Object> expKeywordReport(
            @RequestParam String userid,
            @RequestParam(required = false) String fromdate,
            @RequestParam(required = false) String todate) {
        return Map.of(
            "result", "success",
            "status", "200",
            "data",   Map.of("expkeywords", java.util.List.of())
        );
    }

    /** 광고그룹 키워드 리포트 */
    @GetMapping("/adgroupkeywordreport")
    public Map<String, Object> adgroupKeywordReport(
            @RequestParam String userid,
            @RequestParam String fromdate,
            @RequestParam String todate,
            @RequestParam(required = false) String comparefromdate,
            @RequestParam(required = false) String comparetodate,
            @RequestParam(required = false, defaultValue = "N") String md,
            @RequestParam(required = false, defaultValue = "cst") String kpi,
            @RequestParam(required = false, defaultValue = "") String adgroupid) {
        return adgroupKeywordReportService.getAdgroupKeywordReport(
            userid, md, fromdate, todate, comparefromdate, comparetodate, kpi, adgroupid);
    }

    /** 캠페인 키워드 리포트 */
    @GetMapping("/campaignkeywordreport")
    public Map<String, Object> campaignKeywordReport(
            @RequestParam String userid,
            @RequestParam String fromdate,
            @RequestParam String todate,
            @RequestParam(required = false) String comparefromdate,
            @RequestParam(required = false) String comparetodate,
            @RequestParam(required = false, defaultValue = "N") String md,
            @RequestParam(required = false, defaultValue = "cst") String kpi,
            @RequestParam(required = false, defaultValue = "") String campaignid) {
        return campaignKeywordReportService.getCampaignKeywordReport(
            userid, md, fromdate, todate, comparefromdate, comparetodate, kpi, campaignid);
    }

    /** 광고그룹 소재 리포트 */
    @GetMapping("/adgroupadreport")
    public Map<String, Object> adgroupAdReport(
            @RequestParam String userid,
            @RequestParam String fromdate,
            @RequestParam String todate,
            @RequestParam(required = false) String comparefromdate,
            @RequestParam(required = false) String comparetodate,
            @RequestParam(required = false, defaultValue = "N") String md,
            @RequestParam(required = false, defaultValue = "cst") String kpi,
            @RequestParam(required = false, defaultValue = "") String adgroupid) {
        return adgroupAdReportService.getAdgroupAdReport(
            userid, md, fromdate, todate, comparefromdate, comparetodate, kpi, adgroupid);
    }

    /** 캠페인 소재 리포트 */
    @GetMapping("/campaignadreport")
    public Map<String, Object> campaignAdReport(
            @RequestParam String userid,
            @RequestParam String fromdate,
            @RequestParam String todate,
            @RequestParam(required = false) String comparefromdate,
            @RequestParam(required = false) String comparetodate,
            @RequestParam(required = false, defaultValue = "N") String md,
            @RequestParam(required = false, defaultValue = "cst") String kpi,
            @RequestParam(required = false, defaultValue = "") String campaignid) {
        return campaignAdReportService.getCampaignAdReport(
            userid, md, fromdate, todate, comparefromdate, comparetodate, kpi, campaignid);
    }

    /** 광고그룹 리포트 */
    @GetMapping("/adgroupreport")
    public Map<String, Object> adgroupReport(
            @RequestParam String userid,
            @RequestParam String fromdate,
            @RequestParam String todate,
            @RequestParam(required = false, defaultValue = "N") String md,
            @RequestParam(required = false, defaultValue = "") String campaignid,
            @RequestParam(required = false, defaultValue = "cstd") String sort,
            @RequestParam(required = false, defaultValue = "0") int start,
            @RequestParam(required = false, defaultValue = "20") int display) {
        return adgroupReportService.getAdgroupReport(userid, md, fromdate, todate, campaignid, sort, start, display);
    }
}
