package com.h3.h3_java.api.collector;

import com.h3.h3_java.batch.master.NaverAdDetailJob;
import com.h3.h3_java.batch.master.NaverMasterReportJob;
import com.h3.h3_java.batch.stat.NaverCampaignDayCollectionJob;
import com.h3.h3_java.batch.stat.NaverCampaignHourCollectionJob;
import com.h3.h3_java.media.naver.dto.NaverAccountDto;
import com.h3.h3_java.media.naver.mapper.NaverMasterReportMapper;
import com.h3.h3_java.queue.producer.CollectorProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@RestController
@RequestMapping("/api/collector/naver")
@RequiredArgsConstructor
public class NaverCollectorController {

    private final NaverMasterReportJob job;
    private final NaverAdDetailJob adDetailJob;
    private final NaverCampaignDayCollectionJob campaignDayJob;
    private final NaverCampaignHourCollectionJob campaignHourJob;
    private final NaverMasterReportMapper mapper;
    private final CollectorProducer producer;

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
        log.info("[NaverCollector] 전체 강제 수집 MQ 발행 시작 (delta 무시)");
        try {
            List<NaverAccountDto> accounts = mapper.selectNaverAccounts();
            Set<String> seen = new HashSet<>();
            int count = 0;
            for (NaverAccountDto account : accounts) {
                if ("admin".equals(account.getUserId())) continue;
                String cid = account.getAccountNaverCustomer();
                if (cid == null || cid.isBlank() || !seen.add(cid)) continue;
                producer.sendNaverMaster(account.getUserId(), cid, true);
                count++;
            }
            return ResponseEntity.ok(Map.of("status", "ok", "message", "전체 강제 수집 MQ 발행 완료 " + count + "건"));
        } catch (Exception e) {
            log.error("[NaverCollector] 강제 수집 MQ 발행 실패", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/master/{userId}")
    public ResponseEntity<Map<String, String>> collectMasterByUser(@PathVariable String userId) {
        log.info("[NaverCollector] 단일 수집 시작 userId={}", userId);
        try {
            boolean found = job.collectForUserId(userId, false);
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
        log.info("[NaverCollector] 단일 강제 수집 MQ 발행 userId={} (delta 무시)", userId);
        try {
            NaverAccountDto account = mapper.selectNaverAccounts().stream()
                    .filter(a -> userId.equals(a.getUserId()))
                    .findFirst().orElse(null);
            if (account == null) return ResponseEntity.badRequest()
                    .body(Map.of("status", "error", "message", "userId 없음: " + userId));
            producer.sendNaverMaster(account.getUserId(), account.getAccountNaverCustomer(), true);
            return ResponseEntity.ok(Map.of("status", "ok", "message", userId + " 강제 수집 MQ 발행 완료"));
        } catch (Exception e) {
            log.error("[NaverCollector] 강제 수집 MQ 발행 실패", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/ad-detail")
    public ResponseEntity<Map<String, String>> collectAdDetail() {
        log.info("[NaverCollector] 소재 상세 전체 수집 MQ 발행 시작");
        try {
            List<NaverAccountDto> accounts = mapper.selectNaverAccounts();
            Set<String> seen = new HashSet<>();
            int count = 0;
            for (NaverAccountDto account : accounts) {
                if ("admin".equals(account.getUserId())) continue;
                String cid = account.getAccountNaverCustomer();
                if (cid == null || cid.isBlank() || !seen.add(cid)) continue;
                producer.sendNaverAdDetail(account.getUserId(), cid);
                count++;
            }
            return ResponseEntity.ok(Map.of("status", "ok", "message", "소재 상세 MQ 발행 완료 " + count + "건"));
        } catch (Exception e) {
            log.error("[NaverCollector] 소재 상세 MQ 발행 실패", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/ad-detail/{userId}")
    public ResponseEntity<Map<String, String>> collectAdDetailByUser(@PathVariable String userId) {
        log.info("[NaverCollector] 소재 상세 단일 수집 MQ 발행 userId={}", userId);
        try {
            NaverAccountDto account = mapper.selectNaverAccounts().stream()
                    .filter(a -> userId.equals(a.getUserId()))
                    .findFirst().orElse(null);
            if (account == null) return ResponseEntity.badRequest()
                    .body(Map.of("status", "error", "message", "userId 없음: " + userId));
            producer.sendNaverAdDetail(account.getUserId(), account.getAccountNaverCustomer());
            return ResponseEntity.ok(Map.of("status", "ok", "message", userId + " 소재 상세 MQ 발행 완료"));
        } catch (Exception e) {
            log.error("[NaverCollector] 소재 상세 MQ 발행 실패", e);
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

    @PostMapping("/campaign-hour")
    public ResponseEntity<Map<String, String>> collectCampaignHour() {
        log.info("[NaverCollector] 캠페인 시간별 전체 수집 시작");
        try {
            campaignHourJob.collect();
            return ResponseEntity.ok(Map.of("status", "ok", "message", "캠페인 시간별 전체 수집 완료"));
        } catch (Exception e) {
            log.error("[NaverCollector] 캠페인 시간별 수집 실패", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/campaign-hour/{userId}")
    public ResponseEntity<Map<String, String>> collectCampaignHourByUser(@PathVariable String userId) {
        log.info("[NaverCollector] 캠페인 시간별 단일 수집 userId={}", userId);
        try {
            boolean found = campaignHourJob.collectForUserId(userId);
            if (!found) return ResponseEntity.badRequest()
                    .body(Map.of("status", "error", "message", "userId 없음: " + userId));
            return ResponseEntity.ok(Map.of("status", "ok", "message", userId + " 캠페인 시간별 수집 완료"));
        } catch (Exception e) {
            log.error("[NaverCollector] 캠페인 시간별 수집 실패", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/campaign-hour/{userId}/range")
    public ResponseEntity<Map<String, String>> collectCampaignHourRange(
            @PathVariable String userId,
            @RequestParam String from,
            @RequestParam String to) {
        log.info("[NaverCollector] 캠페인 시간별 기간 수집 userId={} from={} to={}", userId, from, to);
        try {
            campaignHourJob.collectRange(userId, from, to);
            return ResponseEntity.ok(Map.of("status", "ok", "message", userId + " " + from + "~" + to + " 수집 완료"));
        } catch (Exception e) {
            log.error("[NaverCollector] 캠페인 시간별 기간 수집 실패", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }
}