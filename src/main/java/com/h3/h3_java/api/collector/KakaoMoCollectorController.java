package com.h3.h3_java.api.collector;

import com.h3.h3_java.batch.master.KakaoMoAdDetailJob;
import com.h3.h3_java.batch.master.KakaoMoMasterJob;
import com.h3.h3_java.batch.stat.KakaoMoAdDayJob;
import com.h3.h3_java.batch.stat.KakaoMoAdGroupDayJob;
import com.h3.h3_java.batch.stat.KakaoMoBudgetAlarmJob;
import com.h3.h3_java.batch.stat.KakaoMoCampaignDayJob;
import com.h3.h3_java.batch.stat.KakaoMoCampaignHourJob;
import com.h3.h3_java.media.kakao.dto.KakaoMoAccountDto;
import com.h3.h3_java.raw.mongo.AccountMongoService;
import com.h3.h3_java.queue.producer.CollectorProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/collector/kakao/mo")
@RequiredArgsConstructor
public class KakaoMoCollectorController {

    private final KakaoMoMasterJob       masterJob;
    private final KakaoMoAdDetailJob     adDetailJob;
    private final KakaoMoCampaignDayJob  campaignDayJob;
    private final KakaoMoCampaignHourJob campaignHourJob;
    private final KakaoMoAdGroupDayJob   adGroupDayJob;
    private final KakaoMoAdDayJob        adDayJob;
    private final KakaoMoBudgetAlarmJob  budgetAlarmJob;
    private final AccountMongoService     accountMongo;
    private final CollectorProducer      producer;

    private KakaoMoAccountDto findAccount(String userId) {
        return accountMongo.findKakaoMoAccountDtos().stream()
            .filter(a -> userId.equals(a.getUserId()))
            .findFirst().orElse(null);
    }

    // =====================================================================
    // MASTER
    // =====================================================================

    @PostMapping("/master")
    public ResponseEntity<Map<String, String>> collectMaster() {
        masterJob.collect();
        return ok("카카오MO 마스터 전체 수집 완료");
    }

    @PostMapping("/master/{userId}")
    public ResponseEntity<Map<String, String>> collectMasterByUser(@PathVariable String userId) {
        if (findAccount(userId) == null) return notFound(userId);
        producer.sendKakaoMoMaster(userId);
        return ok("카카오MO 마스터 수집 요청 완료 userId=" + userId);
    }

    // =====================================================================
    // AD DETAIL
    // =====================================================================

    @PostMapping("/ad-detail")
    public ResponseEntity<Map<String, String>> collectAdDetail() {
        adDetailJob.collect();
        return ok("카카오MO 소재상세 전체 수집 완료");
    }

    @PostMapping("/ad-detail/{userId}")
    public ResponseEntity<Map<String, String>> collectAdDetailByUser(@PathVariable String userId) {
        if (findAccount(userId) == null) return notFound(userId);
        producer.sendKakaoMoAdDetail(userId);
        return ok("카카오MO 소재상세 수집 요청 완료 userId=" + userId);
    }

    // =====================================================================
    // CAMPAIGN DAILY
    // =====================================================================

    @PostMapping("/campaign-daily")
    public ResponseEntity<Map<String, String>> collectCampaignDaily() {
        campaignDayJob.collect();
        return ok("카카오MO 캠페인 일별 전체 수집 완료");
    }

    @PostMapping("/campaign-daily/range")
    public ResponseEntity<Map<String, String>> collectCampaignDailyAllRange(
        @RequestParam String from, @RequestParam String to) {
        List<KakaoMoAccountDto> accounts = accountMongo.findKakaoMoAccountDtos();
        int count = 0;
        for (KakaoMoAccountDto a : accounts) {
            if ("admin".equals(a.getUserId())) continue;
            producer.sendKakaoMoCampaignDailyRange(a.getUserId(), from, to);
            count++;
        }
        return ok(from + "~" + to + " 카카오MO 캠페인 일별 전체 수집 MQ 발행 완료 " + count + "건");
    }

    @PostMapping("/campaign-daily/{userId}")
    public ResponseEntity<Map<String, String>> collectCampaignDailyByUser(@PathVariable String userId) {
        if (findAccount(userId) == null) return notFound(userId);
        producer.sendKakaoMoCampaignDaily(userId);
        return ok("카카오MO 캠페인 일별 수집 요청 완료 userId=" + userId);
    }

    @PostMapping("/campaign-daily/{userId}/range")
    public ResponseEntity<Map<String, String>> collectCampaignDailyRange(
        @PathVariable String userId,
        @RequestParam String from,
        @RequestParam String to) {
        if (findAccount(userId) == null) return notFound(userId);
        producer.sendKakaoMoCampaignDailyRange(userId, from, to);
        return ok("카카오MO 캠페인 일별 기간 수집 요청 완료 userId=" + userId + " from=" + from + " to=" + to);
    }

    // =====================================================================
    // CAMPAIGN HOUR
    // =====================================================================

    @PostMapping("/campaign-hour")
    public ResponseEntity<Map<String, String>> collectCampaignHour() {
        campaignHourJob.collect();
        return ok("카카오MO 캠페인 시간별 전체 수집 완료");
    }

    @PostMapping("/campaign-hour/range")
    public ResponseEntity<Map<String, String>> collectCampaignHourAllRange(
        @RequestParam String from, @RequestParam String to) {
        List<KakaoMoAccountDto> accounts = accountMongo.findKakaoMoAccountDtos();
        int count = 0;
        for (KakaoMoAccountDto a : accounts) {
            if ("admin".equals(a.getUserId())) continue;
            producer.sendKakaoMoCampaignHourRange(a.getUserId(), from, to);
            count++;
        }
        return ok(from + "~" + to + " 카카오MO 캠페인 시간별 전체 수집 MQ 발행 완료 " + count + "건");
    }

    @PostMapping("/campaign-hour/{userId}")
    public ResponseEntity<Map<String, String>> collectCampaignHourByUser(@PathVariable String userId) {
        if (findAccount(userId) == null) return notFound(userId);
        producer.sendKakaoMoCampaignHour(userId);
        return ok("카카오MO 캠페인 시간별 수집 요청 완료 userId=" + userId);
    }

    @PostMapping("/campaign-hour/{userId}/range")
    public ResponseEntity<Map<String, String>> collectCampaignHourRange(
        @PathVariable String userId,
        @RequestParam String from,
        @RequestParam String to) {
        if (findAccount(userId) == null) return notFound(userId);
        producer.sendKakaoMoCampaignHourRange(userId, from, to);
        return ok("카카오MO 캠페인 시간별 기간 수집 요청 완료 userId=" + userId + " from=" + from + " to=" + to);
    }

    // =====================================================================
    // ADGROUP DAILY
    // =====================================================================

    @PostMapping("/adgroup-daily")
    public ResponseEntity<Map<String, String>> collectAdGroupDaily() {
        adGroupDayJob.collect();
        return ok("카카오MO 광고그룹 일별 전체 수집 완료");
    }

    @PostMapping("/adgroup-daily/range")
    public ResponseEntity<Map<String, String>> collectAdGroupDailyAllRange(
        @RequestParam String from, @RequestParam String to) {
        List<KakaoMoAccountDto> accounts = accountMongo.findKakaoMoAccountDtos();
        int count = 0;
        for (KakaoMoAccountDto a : accounts) {
            if ("admin".equals(a.getUserId())) continue;
            producer.sendKakaoMoAdGroupDailyRange(a.getUserId(), from, to);
            count++;
        }
        return ok(from + "~" + to + " 카카오MO 광고그룹 일별 전체 수집 MQ 발행 완료 " + count + "건");
    }

    @PostMapping("/adgroup-daily/{userId}")
    public ResponseEntity<Map<String, String>> collectAdGroupDailyByUser(@PathVariable String userId) {
        if (findAccount(userId) == null) return notFound(userId);
        producer.sendKakaoMoAdGroupDaily(userId);
        return ok("카카오MO 광고그룹 일별 수집 요청 완료 userId=" + userId);
    }

    @PostMapping("/adgroup-daily/{userId}/range")
    public ResponseEntity<Map<String, String>> collectAdGroupDailyRange(
        @PathVariable String userId,
        @RequestParam String from,
        @RequestParam String to) {
        if (findAccount(userId) == null) return notFound(userId);
        producer.sendKakaoMoAdGroupDailyRange(userId, from, to);
        return ok("카카오MO 광고그룹 일별 기간 수집 요청 완료 userId=" + userId + " from=" + from + " to=" + to);
    }

    // =====================================================================
    // AD DAILY
    // =====================================================================

    @PostMapping("/ad-daily")
    public ResponseEntity<Map<String, String>> collectAdDaily() {
        adDayJob.collect();
        return ok("카카오MO 소재 일별 전체 수집 완료");
    }

    @PostMapping("/ad-daily/range")
    public ResponseEntity<Map<String, String>> collectAdDailyAllRange(
        @RequestParam String from, @RequestParam String to) {
        List<KakaoMoAccountDto> accounts = accountMongo.findKakaoMoAccountDtos();
        int count = 0;
        for (KakaoMoAccountDto a : accounts) {
            if ("admin".equals(a.getUserId())) continue;
            producer.sendKakaoMoAdDailyRange(a.getUserId(), from, to);
            count++;
        }
        return ok(from + "~" + to + " 카카오MO 소재 일별 전체 수집 MQ 발행 완료 " + count + "건");
    }

    @PostMapping("/ad-daily/{userId}")
    public ResponseEntity<Map<String, String>> collectAdDailyByUser(@PathVariable String userId) {
        if (findAccount(userId) == null) return notFound(userId);
        producer.sendKakaoMoAdDaily(userId);
        return ok("카카오MO 소재 일별 수집 요청 완료 userId=" + userId);
    }

    @PostMapping("/ad-daily/{userId}/range")
    public ResponseEntity<Map<String, String>> collectAdDailyRange(
        @PathVariable String userId,
        @RequestParam String from,
        @RequestParam String to) {
        if (findAccount(userId) == null) return notFound(userId);
        producer.sendKakaoMoAdDailyRange(userId, from, to);
        return ok("카카오MO 소재 일별 기간 수집 요청 완료 userId=" + userId + " from=" + from + " to=" + to);
    }

    // =====================================================================
    // BUDGET ALARM
    // =====================================================================

    @PostMapping("/budget-alarm")
    public ResponseEntity<Map<String, String>> collectBudgetAlarm() {
        budgetAlarmJob.collect();
        return ok("카카오MO 예산 알람 전체 수집 완료");
    }

    @PostMapping("/budget-alarm/range")
    public ResponseEntity<Map<String, String>> collectBudgetAlarmAllRange(
        @RequestParam String from, @RequestParam String to) {
        List<KakaoMoAccountDto> accounts = accountMongo.findKakaoMoAccountDtos();
        int count = 0;
        for (KakaoMoAccountDto a : accounts) {
            if ("admin".equals(a.getUserId())) continue;
            producer.sendKakaoMoBudgetAlarmRange(a.getUserId(), from, to);
            count++;
        }
        return ok(from + "~" + to + " 카카오MO 예산 알람 전체 수집 MQ 발행 완료 " + count + "건");
    }

    @PostMapping("/budget-alarm/{userId}")
    public ResponseEntity<Map<String, String>> collectBudgetAlarmByUser(@PathVariable String userId) {
        if (findAccount(userId) == null) return notFound(userId);
        producer.sendKakaoMoBudgetAlarm(userId);
        return ok("카카오MO 예산 알람 수집 요청 완료 userId=" + userId);
    }

    @PostMapping("/budget-alarm/{userId}/range")
    public ResponseEntity<Map<String, String>> collectBudgetAlarmRange(
        @PathVariable String userId,
        @RequestParam String from,
        @RequestParam String to) {
        if (findAccount(userId) == null) return notFound(userId);
        producer.sendKakaoMoBudgetAlarmRange(userId, from, to);
        return ok("카카오MO 예산 알람 기간 수집 요청 완료 userId=" + userId + " from=" + from + " to=" + to);
    }

    // =====================================================================
    // 헬퍼
    // =====================================================================

    private ResponseEntity<Map<String, String>> ok(String message) {
        return ResponseEntity.ok(Map.of("status", "ok", "message", message));
    }

    private ResponseEntity<Map<String, String>> notFound(String userId) {
        return ResponseEntity.badRequest()
            .body(Map.of("status", "error", "message", "계정 없음 userId=" + userId));
    }
}
