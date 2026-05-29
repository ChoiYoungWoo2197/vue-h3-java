package com.h3.h3_java.api.collector;

import com.h3.h3_java.batch.master.GoogleMasterJob;
import com.h3.h3_java.batch.stat.*;
import com.h3.h3_java.queue.producer.CollectorProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 구글 수집 REST API.
 *
 * POST /api/collector/google/master               전체 수집
 * POST /api/collector/google/master/{userId}      단일 → MQ
 *
 * POST /api/collector/google/campaign-daily
 * POST /api/collector/google/campaign-daily/{userId}
 * POST /api/collector/google/campaign-daily/{userId}/range?from=&to=
 *
 * POST /api/collector/google/campaign-hour
 * POST /api/collector/google/campaign-hour/{userId}
 * POST /api/collector/google/campaign-hour/{userId}/range?from=&to=
 *
 * POST /api/collector/google/adgroup-daily
 * POST /api/collector/google/adgroup-daily/{userId}
 * POST /api/collector/google/adgroup-daily/{userId}/range?from=&to=
 *
 * POST /api/collector/google/ad-daily
 * POST /api/collector/google/ad-daily/{userId}
 * POST /api/collector/google/ad-daily/{userId}/range?from=&to=
 *
 * POST /api/collector/google/keyword-daily
 * POST /api/collector/google/keyword-daily/{userId}
 * POST /api/collector/google/keyword-daily/{userId}/range?from=&to=
 */
@Slf4j
@RestController
@RequestMapping("/api/collector/google")
@RequiredArgsConstructor
public class GoogleCollectorController {

    private final CollectorProducer     producer;
    private final GoogleMasterJob       masterJob;
    private final GoogleCampaignDayJob  campaignDayJob;
    private final GoogleCampaignHourJob campaignHourJob;
    private final GoogleAdGroupDayJob   adGroupDayJob;
    private final GoogleAdDayJob        adDayJob;
    private final GoogleKeywordDayJob   keywordDayJob;

    // ── Master ──────────────────────────────────────────────────────────────

    @PostMapping("/master")
    public ResponseEntity<?> masterAll() {
        masterJob.collect();
        return ok("google master 전체 수집 완료");
    }

    @PostMapping("/master/{userId}")
    public ResponseEntity<?> masterUser(@PathVariable String userId) {
        producer.sendGoogleMaster(userId);
        return ok("google master MQ 발행: " + userId);
    }

    // ── Campaign Daily ───────────────────────────────────────────────────────

    @PostMapping("/campaign-daily")
    public ResponseEntity<?> campaignDailyAll() {
        campaignDayJob.collect();
        return ok("google campaign-daily 전체 수집 완료");
    }

    @PostMapping("/campaign-daily/{userId}")
    public ResponseEntity<?> campaignDailyUser(@PathVariable String userId) {
        producer.sendGoogleCampaignDaily(userId);
        return ok("google campaign-daily MQ 발행: " + userId);
    }

    @PostMapping("/campaign-daily/{userId}/range")
    public ResponseEntity<?> campaignDailyRange(@PathVariable String userId,
                                                @RequestParam String from,
                                                @RequestParam String to) {
        producer.sendGoogleCampaignDailyRange(userId, from, to);
        return ok("google campaign-daily range MQ 발행: " + userId);
    }

    // ── Campaign Hour ────────────────────────────────────────────────────────

    @PostMapping("/campaign-hour")
    public ResponseEntity<?> campaignHourAll() {
        campaignHourJob.collect();
        return ok("google campaign-hour 전체 수집 완료");
    }

    @PostMapping("/campaign-hour/{userId}")
    public ResponseEntity<?> campaignHourUser(@PathVariable String userId) {
        producer.sendGoogleCampaignHour(userId);
        return ok("google campaign-hour MQ 발행: " + userId);
    }

    @PostMapping("/campaign-hour/{userId}/range")
    public ResponseEntity<?> campaignHourRange(@PathVariable String userId,
                                               @RequestParam String from,
                                               @RequestParam String to) {
        producer.sendGoogleCampaignHourRange(userId, from, to);
        return ok("google campaign-hour range MQ 발행: " + userId);
    }

    // ── AdGroup Daily ────────────────────────────────────────────────────────

    @PostMapping("/adgroup-daily")
    public ResponseEntity<?> adGroupDailyAll() {
        adGroupDayJob.collect();
        return ok("google adgroup-daily 전체 수집 완료");
    }

    @PostMapping("/adgroup-daily/{userId}")
    public ResponseEntity<?> adGroupDailyUser(@PathVariable String userId) {
        producer.sendGoogleAdGroupDaily(userId);
        return ok("google adgroup-daily MQ 발행: " + userId);
    }

    @PostMapping("/adgroup-daily/{userId}/range")
    public ResponseEntity<?> adGroupDailyRange(@PathVariable String userId,
                                               @RequestParam String from,
                                               @RequestParam String to) {
        producer.sendGoogleAdGroupDailyRange(userId, from, to);
        return ok("google adgroup-daily range MQ 발행: " + userId);
    }

    // ── Ad Daily ─────────────────────────────────────────────────────────────

    @PostMapping("/ad-daily")
    public ResponseEntity<?> adDailyAll() {
        adDayJob.collect();
        return ok("google ad-daily 전체 수집 완료");
    }

    @PostMapping("/ad-daily/{userId}")
    public ResponseEntity<?> adDailyUser(@PathVariable String userId) {
        producer.sendGoogleAdDaily(userId);
        return ok("google ad-daily MQ 발행: " + userId);
    }

    @PostMapping("/ad-daily/{userId}/range")
    public ResponseEntity<?> adDailyRange(@PathVariable String userId,
                                          @RequestParam String from,
                                          @RequestParam String to) {
        producer.sendGoogleAdDailyRange(userId, from, to);
        return ok("google ad-daily range MQ 발행: " + userId);
    }

    // ── Keyword Daily ────────────────────────────────────────────────────────

    @PostMapping("/keyword-daily")
    public ResponseEntity<?> keywordDailyAll() {
        keywordDayJob.collect();
        return ok("google keyword-daily 전체 수집 완료");
    }

    @PostMapping("/keyword-daily/{userId}")
    public ResponseEntity<?> keywordDailyUser(@PathVariable String userId) {
        producer.sendGoogleKeywordDaily(userId);
        return ok("google keyword-daily MQ 발행: " + userId);
    }

    @PostMapping("/keyword-daily/{userId}/range")
    public ResponseEntity<?> keywordDailyRange(@PathVariable String userId,
                                               @RequestParam String from,
                                               @RequestParam String to) {
        producer.sendGoogleKeywordDailyRange(userId, from, to);
        return ok("google keyword-daily range MQ 발행: " + userId);
    }

    private ResponseEntity<?> ok(String msg) {
        log.info("[GOOGLE][CTRL] {}", msg);
        return ResponseEntity.ok(Map.of("result", "ok", "message", msg));
    }
}
