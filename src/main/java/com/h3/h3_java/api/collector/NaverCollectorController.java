package com.h3.h3_java.api.collector;

import com.h3.h3_java.batch.master.NaverMasterReportJob;
import com.h3.h3_java.batch.stat.NaverCampaignDayCollectionJob;
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
    private final NaverCampaignDayCollectionJob campaignDayJob;

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

    @PostMapping("/master/force")
    public ResponseEntity<Map<String, String>> collectMasterForce() {
        log.info("[NaverCollector] 전체 강제 수집 시작 (delta 무시)");
        try {
            job.collectForce();
            return ResponseEntity.ok(Map.of("status", "ok", "message", "전체 강제 수집 완료"));
        } catch (Exception e) {
            log.error("[NaverCollector] 강제 수집 실패", e);
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

    @PostMapping("/master/{userId}/force")
    public ResponseEntity<Map<String, String>> collectMasterByUserForce(@PathVariable String userId) {
        log.info("[NaverCollector] 단일 강제 수집 시작 userId={} (delta 무시)", userId);
        try {
            boolean found = job.collectForUserIdForce(userId);
            if (!found) return ResponseEntity.badRequest()
                    .body(Map.of("status", "error", "message", "userId 없음: " + userId));
            return ResponseEntity.ok(Map.of("status", "ok", "message", userId + " 강제 수집 완료"));
        } catch (Exception e) {
            log.error("[NaverCollector] 강제 수집 실패", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/campaign-daily")
    public ResponseEntity<Map<String, String>> collectCampaignDaily() {
        log.info("[NaverCollector] 캠페인 일별 전체 수집 시작");
        try {
            campaignDayJob.collect();
            return ResponseEntity.ok(Map.of("status", "ok", "message", "캠페인 일별 전체 수집 완료"));
        } catch (Exception e) {
            log.error("[NaverCollector] 캠페인 일별 수집 실패", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/campaign-daily/{userId}")
    public ResponseEntity<Map<String, String>> collectCampaignDailyByUser(@PathVariable String userId) {
        log.info("[NaverCollector] 캠페인 일별 단일 수집 userId={}", userId);
        try {
            boolean found = campaignDayJob.collectForUserId(userId);
            if (!found) return ResponseEntity.badRequest()
                    .body(Map.of("status", "error", "message", "userId 없음: " + userId));
            return ResponseEntity.ok(Map.of("status", "ok", "message", userId + " 캠페인 일별 수집 완료"));
        } catch (Exception e) {
            log.error("[NaverCollector] 캠페인 일별 수집 실패", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/campaign-daily/{userId}/range")
    public ResponseEntity<Map<String, String>> collectCampaignDailyRange(
            @PathVariable String userId,
            @RequestParam String from,
            @RequestParam String to) {
        log.info("[NaverCollector] 캠페인 일별 기간 수집 userId={} from={} to={}", userId, from, to);
        try {
            campaignDayJob.collectRange(userId, from, to);
            return ResponseEntity.ok(Map.of("status", "ok", "message", userId + " " + from + "~" + to + " 수집 완료"));
        } catch (Exception e) {
            log.error("[NaverCollector] 캠페인 일별 기간 수집 실패", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }
}