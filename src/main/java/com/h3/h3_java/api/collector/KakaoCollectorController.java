package com.h3.h3_java.api.collector;

import com.h3.h3_java.batch.master.KakaoSaAdDetailJob;
import com.h3.h3_java.batch.master.KakaoSaMasterJob;
import com.h3.h3_java.batch.stat.KakaoSaAdDayJob;
import com.h3.h3_java.batch.stat.KakaoSaAdGroupDayJob;
import com.h3.h3_java.batch.stat.KakaoSaBudgetAlarmJob;
import com.h3.h3_java.batch.stat.KakaoSaCampaignDayJob;
import com.h3.h3_java.batch.stat.KakaoSaCampaignHourJob;
import com.h3.h3_java.batch.stat.KakaoSaKeywordDayJob;
import com.h3.h3_java.media.kakao.dto.KakaoSaAccountDto;
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
@RequestMapping("/api/collector/kakao/sa")
@RequiredArgsConstructor
public class KakaoCollectorController {

    private final KakaoSaMasterJob       masterJob;
    private final KakaoSaAdDetailJob     adDetailJob;
    private final KakaoSaCampaignDayJob  campaignDayJob;
    private final KakaoSaCampaignHourJob campaignHourJob;
    private final KakaoSaAdGroupDayJob   adGroupDayJob;
    private final KakaoSaKeywordDayJob   keywordDayJob;
    private final KakaoSaAdDayJob        adDayJob;
    private final KakaoSaBudgetAlarmJob  budgetAlarmJob;
    private final AccountMongoService     accountMongo;
    private final CollectorProducer      producer;

    private KakaoSaAccountDto findAccount(String userId) {
        return accountMongo.findKakaoSaAccountDtos().stream()
            .filter(a -> userId.equals(a.getUserId()))
            .findFirst().orElse(null);
    }

    // =====================================================================
    // MASTER
    // =====================================================================

    @PostMapping("/master")
    public ResponseEntity<Map<String, String>> collectMaster() {
        masterJob.collect();
        return ok("카카오SA 마스터 전체 수집 완료");
    }

    @PostMapping("/master/{userId}")
    public ResponseEntity<Map<String, String>> collectMasterByUser(@PathVariable String userId) {
        if (findAccount(userId) == null) return notFound(userId);
        producer.sendKakaoSaMaster(userId);
        return ok("카카오SA 마스터 수집 요청 완료 userId=" + userId);
    }

    // =====================================================================
    // AD DETAIL
    // =====================================================================

    @PostMapping("/ad-detail")
    public ResponseEntity<Map<String, String>> collectAdDetail() {
        adDetailJob.collect();
        return ok("카카오SA 소재상세 전체 수집 완료");
    }

    @PostMapping("/ad-detail/{userId}")
    public ResponseEntity<Map<String, String>> collectAdDetailByUser(@PathVariable String userId) {
        if (findAccount(userId) == null) return notFound(userId);
        producer.sendKakaoSaAdDetail(userId);
        return ok("카카오SA 소재상세 수집 요청 완료 userId=" + userId);
    }

    // =====================================================================
    // CAMPAIGN DAILY
    // =====================================================================

    @PostMapping("/campaign-daily")
    public ResponseEntity<Map<String, String>> collectCampaignDaily() {
        campaignDayJob.collect();
        return ok("카카오SA 캠페인 일별 전체 수집 완료");
    }

    @PostMapping("/campaign-daily/range")
    public ResponseEntity<Map<String, String>> collectCampaignDailyAllRange(
        @RequestParam String from, @RequestParam String to) {
        List<KakaoSaAccountDto> accounts = accountMongo.findKakaoSaAccountDtos();
        int count = 0;
        for (KakaoSaAccountDto a : accounts) {
            if ("admin".equals(a.getUserId())) continue;
            producer.sendKakaoSaCampaignDailyRange(a.getUserId(), from, to);
            count++;
        }
        return ok(from + "~" + to + " 카카오SA 캠페인 일별 전체 수집 MQ 발행 완료 " + count + "건");
    }

    @PostMapping("/campaign-daily/{userId}")
    public ResponseEntity<Map<String, String>> collectCampaignDailyByUser(@PathVariable String userId) {
        if (findAccount(userId) == null) return notFound(userId);
        producer.sendKakaoSaCampaignDaily(userId);
        return ok("카카오SA 캠페인 일별 수집 요청 완료 userId=" + userId);
    }

    @PostMapping("/campaign-daily/{userId}/range")
    public ResponseEntity<Map<String, String>> collectCampaignDailyRange(
        @PathVariable String userId,
        @RequestParam String from,
        @RequestParam String to) {
        if (findAccount(userId) == null) return notFound(userId);
        producer.sendKakaoSaCampaignDailyRange(userId, from, to);
        return ok("카카오SA 캠페인 일별 기간 수집 요청 완료 userId=" + userId + " from=" + from + " to=" + to);
    }

    // =====================================================================
    // CAMPAIGN HOUR
    // =====================================================================

    @PostMapping("/campaign-hour")
    public ResponseEntity<Map<String, String>> collectCampaignHour() {
        campaignHourJob.collect();
        return ok("카카오SA 캠페인 시간별 전체 수집 완료");
    }

    @PostMapping("/campaign-hour/range")
    public ResponseEntity<Map<String, String>> collectCampaignHourAllRange(
        @RequestParam String from, @RequestParam String to) {
        List<KakaoSaAccountDto> accounts = accountMongo.findKakaoSaAccountDtos();
        int count = 0;
        for (KakaoSaAccountDto a : accounts) {
            if ("admin".equals(a.getUserId())) continue;
            producer.sendKakaoSaCampaignHourRange(a.getUserId(), from, to);
            count++;
        }
        return ok(from + "~" + to + " 카카오SA 캠페인 시간별 전체 수집 MQ 발행 완료 " + count + "건");
    }

    @PostMapping("/campaign-hour/{userId}")
    public ResponseEntity<Map<String, String>> collectCampaignHourByUser(@PathVariable String userId) {
        if (findAccount(userId) == null) return notFound(userId);
        producer.sendKakaoSaCampaignHour(userId);
        return ok("카카오SA 캠페인 시간별 수집 요청 완료 userId=" + userId);
    }

    @PostMapping("/campaign-hour/{userId}/range")
    public ResponseEntity<Map<String, String>> collectCampaignHourRange(
        @PathVariable String userId,
        @RequestParam String from,
        @RequestParam String to) {
        if (findAccount(userId) == null) return notFound(userId);
        producer.sendKakaoSaCampaignHourRange(userId, from, to);
        return ok("카카오SA 캠페인 시간별 기간 수집 요청 완료 userId=" + userId + " from=" + from + " to=" + to);
    }

    // =====================================================================
    // ADGROUP DAILY
    // =====================================================================

    @PostMapping("/adgroup-daily")
    public ResponseEntity<Map<String, String>> collectAdGroupDaily() {
        adGroupDayJob.collect();
        return ok("카카오SA 광고그룹 일별 전체 수집 완료");
    }

    @PostMapping("/adgroup-daily/range")
    public ResponseEntity<Map<String, String>> collectAdGroupDailyAllRange(
        @RequestParam String from, @RequestParam String to) {
        List<KakaoSaAccountDto> accounts = accountMongo.findKakaoSaAccountDtos();
        int count = 0;
        for (KakaoSaAccountDto a : accounts) {
            if ("admin".equals(a.getUserId())) continue;
            producer.sendKakaoSaAdGroupDailyRange(a.getUserId(), from, to);
            count++;
        }
        return ok(from + "~" + to + " 카카오SA 광고그룹 일별 전체 수집 MQ 발행 완료 " + count + "건");
    }

    @PostMapping("/adgroup-daily/{userId}")
    public ResponseEntity<Map<String, String>> collectAdGroupDailyByUser(@PathVariable String userId) {
        if (findAccount(userId) == null) return notFound(userId);
        producer.sendKakaoSaAdGroupDaily(userId);
        return ok("카카오SA 광고그룹 일별 수집 요청 완료 userId=" + userId);
    }

    @PostMapping("/adgroup-daily/{userId}/range")
    public ResponseEntity<Map<String, String>> collectAdGroupDailyRange(
        @PathVariable String userId,
        @RequestParam String from,
        @RequestParam String to) {
        if (findAccount(userId) == null) return notFound(userId);
        producer.sendKakaoSaAdGroupDailyRange(userId, from, to);
        return ok("카카오SA 광고그룹 일별 기간 수집 요청 완료 userId=" + userId + " from=" + from + " to=" + to);
    }

    // =====================================================================
    // KEYWORD DAILY
    // =====================================================================

    @PostMapping("/keyword-daily")
    public ResponseEntity<Map<String, String>> collectKeywordDaily() {
        keywordDayJob.collect();
        return ok("카카오SA 키워드 일별 전체 수집 완료");
    }

    @PostMapping("/keyword-daily/range")
    public ResponseEntity<Map<String, String>> collectKeywordDailyAllRange(
        @RequestParam String from, @RequestParam String to) {
        List<KakaoSaAccountDto> accounts = accountMongo.findKakaoSaAccountDtos();
        int count = 0;
        for (KakaoSaAccountDto a : accounts) {
            if ("admin".equals(a.getUserId())) continue;
            producer.sendKakaoSaKeywordDailyRange(a.getUserId(), from, to);
            count++;
        }
        return ok(from + "~" + to + " 카카오SA 키워드 일별 전체 수집 MQ 발행 완료 " + count + "건");
    }

    @PostMapping("/keyword-daily/{userId}")
    public ResponseEntity<Map<String, String>> collectKeywordDailyByUser(@PathVariable String userId) {
        if (findAccount(userId) == null) return notFound(userId);
        producer.sendKakaoSaKeywordDaily(userId);
        return ok("카카오SA 키워드 일별 수집 요청 완료 userId=" + userId);
    }

    @PostMapping("/keyword-daily/{userId}/range")
    public ResponseEntity<Map<String, String>> collectKeywordDailyRange(
        @PathVariable String userId,
        @RequestParam String from,
        @RequestParam String to) {
        if (findAccount(userId) == null) return notFound(userId);
        producer.sendKakaoSaKeywordDailyRange(userId, from, to);
        return ok("카카오SA 키워드 일별 기간 수집 요청 완료 userId=" + userId + " from=" + from + " to=" + to);
    }

    // =====================================================================
    // AD DAILY
    // =====================================================================

    @PostMapping("/ad-daily")
    public ResponseEntity<Map<String, String>> collectAdDaily() {
        adDayJob.collect();
        return ok("카카오SA 소재 일별 전체 수집 완료");
    }

    @PostMapping("/ad-daily/range")
    public ResponseEntity<Map<String, String>> collectAdDailyAllRange(
        @RequestParam String from, @RequestParam String to) {
        List<KakaoSaAccountDto> accounts = accountMongo.findKakaoSaAccountDtos();
        int count = 0;
        for (KakaoSaAccountDto a : accounts) {
            if ("admin".equals(a.getUserId())) continue;
            producer.sendKakaoSaAdDailyRange(a.getUserId(), from, to);
            count++;
        }
        return ok(from + "~" + to + " 카카오SA 소재 일별 전체 수집 MQ 발행 완료 " + count + "건");
    }

    @PostMapping("/ad-daily/{userId}")
    public ResponseEntity<Map<String, String>> collectAdDailyByUser(@PathVariable String userId) {
        if (findAccount(userId) == null) return notFound(userId);
        producer.sendKakaoSaAdDaily(userId);
        return ok("카카오SA 소재 일별 수집 요청 완료 userId=" + userId);
    }

    @PostMapping("/ad-daily/{userId}/range")
    public ResponseEntity<Map<String, String>> collectAdDailyRange(
        @PathVariable String userId,
        @RequestParam String from,
        @RequestParam String to) {
        if (findAccount(userId) == null) return notFound(userId);
        producer.sendKakaoSaAdDailyRange(userId, from, to);
        return ok("카카오SA 소재 일별 기간 수집 요청 완료 userId=" + userId + " from=" + from + " to=" + to);
    }

    // =====================================================================
    // BUDGET ALARM
    // =====================================================================

    @PostMapping("/budget-alarm")
    public ResponseEntity<Map<String, String>> collectBudgetAlarm() {
        budgetAlarmJob.collect();
        return ok("카카오SA 예산 알람 전체 수집 완료");
    }

    @PostMapping("/budget-alarm/range")
    public ResponseEntity<Map<String, String>> collectBudgetAlarmAllRange(
        @RequestParam String from, @RequestParam String to) {
        List<KakaoSaAccountDto> accounts = accountMongo.findKakaoSaAccountDtos();
        int count = 0;
        for (KakaoSaAccountDto a : accounts) {
            if ("admin".equals(a.getUserId())) continue;
            producer.sendKakaoSaBudgetAlarmRange(a.getUserId(), from, to);
            count++;
        }
        return ok(from + "~" + to + " 카카오SA 예산 알람 전체 수집 MQ 발행 완료 " + count + "건");
    }

    @PostMapping("/budget-alarm/{userId}")
    public ResponseEntity<Map<String, String>> collectBudgetAlarmByUser(@PathVariable String userId) {
        if (findAccount(userId) == null) return notFound(userId);
        producer.sendKakaoSaBudgetAlarm(userId);
        return ok("카카오SA 예산 알람 수집 요청 완료 userId=" + userId);
    }

    @PostMapping("/budget-alarm/{userId}/range")
    public ResponseEntity<Map<String, String>> collectBudgetAlarmRange(
        @PathVariable String userId,
        @RequestParam String from,
        @RequestParam String to) {
        if (findAccount(userId) == null) return notFound(userId);
        producer.sendKakaoSaBudgetAlarmRange(userId, from, to);
        return ok("카카오SA 예산 알람 기간 수집 요청 완료 userId=" + userId + " from=" + from + " to=" + to);
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
