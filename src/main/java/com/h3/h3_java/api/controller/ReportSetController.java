package com.h3.h3_java.api.controller;

import com.h3.h3_java.api.service.reportsend.ReportSetService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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

    @GetMapping("/reportsend-reservation")
    @PostMapping("/reportsend-reservation")
    public Map<String, Object> reservation(@RequestParam Map<String, String> params) {
        return reportSetService.handleReservation(params);
    }

    @PostMapping("/reportsend-email")
    public Map<String, Object> sendEmail(
            @RequestParam String userid,
            @RequestParam String bid,
            @RequestParam String name,
            @RequestParam String sender,
            @RequestParam String semail,
            @RequestParam String recver,
            @RequestParam String remail,
            @RequestPart("file") MultipartFile file) {
        return reportSetService.handleSendEmail(userid, bid, name, sender, semail, recver, remail, file);
    }
}
