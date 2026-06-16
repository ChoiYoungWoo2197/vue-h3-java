package com.h3.h3_java.api.controller;

import com.h3.h3_java.api.service.reportsend.ReportSetService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/v1/h3/app/reportsend")
@RequiredArgsConstructor
public class ReportSetController {

    private final ReportSetService reportSetService;

    @GetMapping("/reportset")
    @PostMapping("/reportset")
    public Map<String, Object> reportSet(@RequestParam Map<String, String> params) {
        return reportSetService.handleReportSet(params);
    }
}
