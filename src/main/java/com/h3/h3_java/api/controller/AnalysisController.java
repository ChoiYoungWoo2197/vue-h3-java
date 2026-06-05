package com.h3.h3_java.api.controller;

import com.h3.h3_java.api.service.analysis.CampaignReportService;
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

    /** 캠페인 리포트 (매체별 캠페인 타입 그룹핑 + 비교기간) */
    @GetMapping("/campaignreport")
    public Map<String, Object> campaignReport(
            @RequestParam String userid,
            @RequestParam String fromdate,
            @RequestParam String todate,
            @RequestParam(required = false) String comparefromdate,
            @RequestParam(required = false) String comparetodate) {
        return campaignReportService.getCampaignReport(userid, fromdate, todate, comparefromdate, comparetodate);
    }
}
