package com.h3.h3_java.api.controller;

import com.h3.h3_java.api.service.analysis.AdReportService;
import com.h3.h3_java.api.service.analysis.AdgroupReportService;
import com.h3.h3_java.api.service.analysis.CampaignReportService;
import com.h3.h3_java.api.service.analysis.KeywordReportService;
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

    private final CampaignReportService campaignReportService;
    private final AdgroupReportService  adgroupReportService;
    private final KeywordReportService  keywordReportService;
    private final AdReportService       adReportService;

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
            @RequestParam(required = false, defaultValue = "N") String md,
            @RequestParam(required = false, defaultValue = "") String kpi,
            @RequestParam(required = false, defaultValue = "cstd") String sort,
            @RequestParam(required = false, defaultValue = "0") int start,
            @RequestParam(required = false, defaultValue = "20") int display) {
        return adReportService.getAdReport(userid, md, fromdate, todate, kpi, sort, start, display);
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
