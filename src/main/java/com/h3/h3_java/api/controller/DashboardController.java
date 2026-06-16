package com.h3.h3_java.api.controller;

import com.h3.h3_java.api.service.dashboard.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/v1/h3/app/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    /** 매체별 요약 */
    @GetMapping("/summarymedia")
    @PostMapping("/summarymedia")
    public Map<String, Object> summaryMedia(
            @RequestParam String userid,
            @RequestParam String fromdate,
            @RequestParam String todate,
            @RequestParam(defaultValue = "1,1,1,1,1") String media) {
        return dashboardService.getSummaryMedia(userid, fromdate, todate, media);
    }

    /** 전체 요약 (비교기간 포함) */
    @GetMapping("/summary")
    @PostMapping("/summary")
    public Map<String, Object> summary(
            @RequestParam String userid,
            @RequestParam String fromdate,
            @RequestParam String todate,
            @RequestParam(required = false) String comparefromdate,
            @RequestParam(required = false) String comparetodate,
            @RequestParam(defaultValue = "1,1,1,1,1") String media) {
        return dashboardService.getSummary(userid, fromdate, todate, comparefromdate, comparetodate, media);
    }

    /** 매체별 비중 */
    @GetMapping("/summarymediarate")
    @PostMapping("/summarymediarate")
    public Map<String, Object> summaryMediaRate(
            @RequestParam String userid,
            @RequestParam String fromdate,
            @RequestParam String todate,
            @RequestParam(required = false) String comparefromdate,
            @RequestParam(required = false) String comparetodate,
            @RequestParam(defaultValue = "1,1,1,1,1") String media) {
        return dashboardService.getSummaryMediaRate(userid, fromdate, todate, media);
    }

    /** 날짜별 통계 */
    @GetMapping("/period")
    @PostMapping("/period")
    public Map<String, Object> period(
            @RequestParam String userid,
            @RequestParam String fromdate,
            @RequestParam String todate,
            @RequestParam(defaultValue = "1,1,1,1,1") String media) {
        return dashboardService.getPeriod(userid, fromdate, todate, media);
    }
}
