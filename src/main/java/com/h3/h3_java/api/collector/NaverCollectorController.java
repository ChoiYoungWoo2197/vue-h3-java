package com.h3.h3_java.api.collector;

import com.h3.h3_java.batch.master.NaverMasterReportJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/collector/naver")
@RequiredArgsConstructor
public class NaverCollectorController {

    private final NaverMasterReportJob job;

    @PostMapping("/master")
    public ResponseEntity<Map<String, String>> collectMaster() {
        log.info("[NaverCollector] 전체 수집 시작");
        try {
            job.collect();
            return ResponseEntity.ok(Map.of("status", "ok", "message", "전체 수집 완료"));
        } catch (Exception e) {
            log.error("[NaverCollector] 수집 실패", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/master/{userId}")
    public ResponseEntity<Map<String, String>> collectMasterByUser(@PathVariable String userId) {
        log.info("[NaverCollector] 단일 수집 시작 userId={}", userId);
        try {
            boolean found = job.collectForUserId(userId);
            if (!found) return ResponseEntity.badRequest()
                    .body(Map.of("status", "error", "message", "userId 없음: " + userId));
            return ResponseEntity.ok(Map.of("status", "ok", "message", userId + " 수집 완료"));
        } catch (Exception e) {
            log.error("[NaverCollector] 수집 실패", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }
}