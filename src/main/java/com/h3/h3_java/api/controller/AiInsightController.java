package com.h3.h3_java.api.controller;

import com.h3.h3_java.api.service.dashboard.AiInsightService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/v1/h3/app/dashboard")
@RequiredArgsConstructor
public class AiInsightController {

    private final AiInsightService aiInsightService;

    @PostMapping("/aiinsight")
    public Map<String, Object> aiInsight(
            @RequestParam String userid,
            @RequestParam(required = false, defaultValue = "") String session,
            @RequestParam String payload,
            @RequestParam(required = false, defaultValue = "0") String debug
    ) {
        return aiInsightService.analyze(userid, payload, "1".equals(debug));
    }

    @PostMapping("/aiinsight_followup")
    public Map<String, Object> aiInsightFollowup(
            @RequestParam String userid,
            @RequestParam(required = false, defaultValue = "") String session,
            @RequestParam(required = false, defaultValue = "") String payload,
            @RequestParam(required = false, defaultValue = "") String question,
            @RequestParam(required = false, defaultValue = "0") String debug
    ) {
        return aiInsightService.followup(userid, payload, question, "1".equals(debug));
    }
}
