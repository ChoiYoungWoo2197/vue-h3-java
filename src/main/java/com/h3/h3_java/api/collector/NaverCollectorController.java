package com.h3.h3_java.api.collector;

import com.h3.h3_java.batch.master.NaverGfaMasterJob;
import com.h3.h3_java.batch.stat.NaverGfaAdDayCollectionJob;
import com.h3.h3_java.batch.stat.NaverGfaAdgroupDayCollectionJob;
import com.h3.h3_java.batch.stat.NaverGfaBudgetAlarmJob;
import com.h3.h3_java.batch.stat.NaverGfaCampaignDayCollectionJob;
import com.h3.h3_java.batch.stat.NaverGfaConvTypeJob;
import com.h3.h3_java.batch.master.NaverMasterReportJob;
import com.h3.h3_java.batch.stat.NaverAdDayCollectionJob;
import com.h3.h3_java.batch.stat.NaverAdGroupDayCollectionJob;
import com.h3.h3_java.batch.stat.NaverCampaignDayCollectionJob;
import com.h3.h3_java.batch.stat.NaverCampaignHourCollectionJob;
import com.h3.h3_java.batch.stat.NaverShoppingAdDayCollectionJob;
import com.h3.h3_java.media.naver.dto.NaverAccountDto;
import com.h3.h3_java.media.naver.dto.NaverGfaAccountDto;
import com.h3.h3_java.media.naver.mapper.NaverGfaMapper;
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
    private final NaverGfaMasterJob gfaMasterJob;
    private final NaverGfaAdDayCollectionJob gfaAdDayJob;
    private final NaverGfaAdgroupDayCollectionJob gfaAdgroupDayJob;
    private final NaverGfaBudgetAlarmJob gfaBudgetAlarmJob;
    private final NaverGfaCampaignDayCollectionJob gfaCampaignDayJob;
    private final NaverGfaConvTypeJob gfaConvTypeJob;
    private final NaverCampaignDayCollectionJob campaignDayJob;
    private final NaverCampaignHourCollectionJob campaignHourJob;
    private final NaverAdGroupDayCollectionJob adGroupDayJob;
    private final NaverAdDayCollectionJob adDayJob;
    private final NaverShoppingAdDayCollectionJob shoppingDayJob;
    private final NaverMasterReportMapper mapper;
    private final NaverGfaMapper gfaMapper;
    private final CollectorProducer producer;

    // =====================================================================
    // 헬퍼
    // =====================================================================

    private NaverAccountDto findAccount(String userId) {
        return mapper.selectNaverAccounts().stream()
                .filter(a -> userId.equals(a.getUserId()))
                .findFirst().orElse(null);
    }

    private ResponseEntity<Map<String, String>> notFound(String userId) {
        return ResponseEntity.badRequest()
                .body(Map.of("status", "error", "message", "userId 없음: " + userId));
    }

    // =====================================================================
    // 마스터
    // =====================================================================

    @PostMapping("/master")
    public ResponseEntity<Map<String, String>> collectMaster() {
        log.info("[NaverCollector] 마스터 전체 수집 시작");
        try {
            job.collect();
            return ResponseEntity.ok(Map.of("status", "ok", "message", "마스터 전체 수집 완료"));
        } catch (Exception e) {
            log.error("[NaverCollector] 마스터 수집 실패", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/master/force")
    public ResponseEntity<Map<String, String>> collectMasterForce() {
        log.info("[NaverCollector] 마스터 전체 강제 수집 MQ 발행 시작");
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
            return ResponseEntity.ok(Map.of("status", "ok", "message", "마스터 강제 수집 MQ 발행 완료 " + count + "건"));
        } catch (Exception e) {
            log.error("[NaverCollector] 마스터 강제 수집 MQ 발행 실패", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/master/{userId}")
    public ResponseEntity<Map<String, String>> collectMasterByUser(@PathVariable String userId) {
        log.info("[NaverCollector] 마스터 단일 수집 MQ 발행 userId={}", userId);
        try {
            NaverAccountDto account = findAccount(userId);
            if (account == null) return notFound(userId);
            producer.sendNaverMaster(userId, account.getAccountNaverCustomer(), false);
            return ResponseEntity.ok(Map.of("status", "ok", "message", userId + " 마스터 수집 MQ 발행 완료"));
        } catch (Exception e) {
            log.error("[NaverCollector] 마스터 수집 MQ 발행 실패", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/master/{userId}/force")
    public ResponseEntity<Map<String, String>> collectMasterByUserForce(@PathVariable String userId) {
        log.info("[NaverCollector] 마스터 단일 강제 수집 MQ 발행 userId={}", userId);
        try {
            NaverAccountDto account = findAccount(userId);
            if (account == null) return notFound(userId);
            producer.sendNaverMaster(userId, account.getAccountNaverCustomer(), true);
            return ResponseEntity.ok(Map.of("status", "ok", "message", userId + " 마스터 강제 수집 MQ 발행 완료"));
        } catch (Exception e) {
            log.error("[NaverCollector] 마스터 강제 수집 MQ 발행 실패", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    // =====================================================================
    // 소재 상세
    // =====================================================================

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
            NaverAccountDto account = findAccount(userId);
            if (account == null) return notFound(userId);
            producer.sendNaverAdDetail(userId, account.getAccountNaverCustomer());
            return ResponseEntity.ok(Map.of("status", "ok", "message", userId + " 소재 상세 MQ 발행 완료"));
        } catch (Exception e) {
            log.error("[NaverCollector] 소재 상세 MQ 발행 실패", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    // =====================================================================
    // 캠페인 일별
    // =====================================================================

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

    @PostMapping("/campaign-daily/range")
    public ResponseEntity<Map<String, String>> collectCampaignDailyAllRange(
            @RequestParam String from,
            @RequestParam String to) {
        log.info("[NaverCollector] 캠페인 일별 전체 기간 수집 MQ 발행 from={} to={}", from, to);
        try {
            List<NaverAccountDto> accounts = mapper.selectNaverAccounts();
            Set<String> seen = new HashSet<>();
            int count = 0;
            for (NaverAccountDto account : accounts) {
                if ("admin".equals(account.getUserId())) continue;
                String cid = account.getAccountNaverCustomer();
                if (cid == null || cid.isBlank() || !seen.add(cid)) continue;
                producer.sendNaverCampaignDailyRange(account.getUserId(), cid, from, to);
                count++;
            }
            return ResponseEntity.ok(Map.of("status", "ok", "message", from + "~" + to + " 캠페인 일별 전체 수집 MQ 발행 완료 " + count + "건"));
        } catch (Exception e) {
            log.error("[NaverCollector] 캠페인 일별 전체 기간 수집 MQ 발행 실패", e);
            return ResponseEntity.internalServerError().body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/campaign-daily/{userId}")
    public ResponseEntity<Map<String, String>> collectCampaignDailyByUser(@PathVariable String userId) {
        log.info("[NaverCollector] 캠페인 일별 단일 수집 MQ 발행 userId={}", userId);
        try {
            NaverAccountDto account = findAccount(userId);
            if (account == null) return notFound(userId);
            producer.sendNaverCampaignDaily(userId, account.getAccountNaverCustomer());
            return ResponseEntity.ok(Map.of("status", "ok", "message", userId + " 캠페인 일별 수집 MQ 발행 완료"));
        } catch (Exception e) {
            log.error("[NaverCollector] 캠페인 일별 수집 MQ 발행 실패", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/campaign-daily/{userId}/range")
    public ResponseEntity<Map<String, String>> collectCampaignDailyRange(
            @PathVariable String userId,
            @RequestParam String from,
            @RequestParam String to) {
        log.info("[NaverCollector] 캠페인 일별 기간 수집 MQ 발행 userId={} from={} to={}", userId, from, to);
        try {
            NaverAccountDto account = findAccount(userId);
            if (account == null) return notFound(userId);
            producer.sendNaverCampaignDailyRange(userId, account.getAccountNaverCustomer(), from, to);
            return ResponseEntity.ok(Map.of("status", "ok", "message", userId + " " + from + "~" + to + " 캠페인 일별 수집 MQ 발행 완료"));
        } catch (Exception e) {
            log.error("[NaverCollector] 캠페인 일별 기간 수집 MQ 발행 실패", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    // =====================================================================
    // 캠페인 시간별
    // =====================================================================

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

    @PostMapping("/campaign-hour/range")
    public ResponseEntity<Map<String, String>> collectCampaignHourAllRange(
            @RequestParam String from,
            @RequestParam String to) {
        log.info("[NaverCollector] 캠페인 시간별 전체 기간 수집 MQ 발행 from={} to={}", from, to);
        try {
            List<NaverAccountDto> accounts = mapper.selectNaverAccounts();
            Set<String> seen = new HashSet<>();
            int count = 0;
            for (NaverAccountDto account : accounts) {
                if ("admin".equals(account.getUserId())) continue;
                String cid = account.getAccountNaverCustomer();
                if (cid == null || cid.isBlank() || !seen.add(cid)) continue;
                producer.sendNaverCampaignHourRange(account.getUserId(), cid, from, to);
                count++;
            }
            return ResponseEntity.ok(Map.of("status", "ok", "message", from + "~" + to + " 캠페인 시간별 전체 수집 MQ 발행 완료 " + count + "건"));
        } catch (Exception e) {
            log.error("[NaverCollector] 캠페인 시간별 전체 기간 수집 MQ 발행 실패", e);
            return ResponseEntity.internalServerError().body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/campaign-hour/{userId}")
    public ResponseEntity<Map<String, String>> collectCampaignHourByUser(@PathVariable String userId) {
        log.info("[NaverCollector] 캠페인 시간별 단일 수집 MQ 발행 userId={}", userId);
        try {
            NaverAccountDto account = findAccount(userId);
            if (account == null) return notFound(userId);
            producer.sendNaverCampaignHour(userId, account.getAccountNaverCustomer());
            return ResponseEntity.ok(Map.of("status", "ok", "message", userId + " 캠페인 시간별 수집 MQ 발행 완료"));
        } catch (Exception e) {
            log.error("[NaverCollector] 캠페인 시간별 수집 MQ 발행 실패", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/campaign-hour/{userId}/range")
    public ResponseEntity<Map<String, String>> collectCampaignHourRange(
            @PathVariable String userId,
            @RequestParam String from,
            @RequestParam String to) {
        log.info("[NaverCollector] 캠페인 시간별 기간 수집 MQ 발행 userId={} from={} to={}", userId, from, to);
        try {
            NaverAccountDto account = findAccount(userId);
            if (account == null) return notFound(userId);
            producer.sendNaverCampaignHourRange(userId, account.getAccountNaverCustomer(), from, to);
            return ResponseEntity.ok(Map.of("status", "ok", "message", userId + " " + from + "~" + to + " 캠페인 시간별 수집 MQ 발행 완료"));
        } catch (Exception e) {
            log.error("[NaverCollector] 캠페인 시간별 기간 수집 MQ 발행 실패", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    // =====================================================================
    // 광고그룹 일별
    // =====================================================================

    @PostMapping("/adgroup-daily")
    public ResponseEntity<Map<String, String>> collectAdGroupDaily() {
        log.info("[NaverCollector] 광고그룹 일별 전체 수집 시작");
        try {
            adGroupDayJob.collect();
            return ResponseEntity.ok(Map.of("status", "ok", "message", "광고그룹 일별 전체 수집 완료"));
        } catch (Exception e) {
            log.error("[NaverCollector] 광고그룹 일별 수집 실패", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/adgroup-daily/range")
    public ResponseEntity<Map<String, String>> collectAdGroupDailyAllRange(
            @RequestParam String from,
            @RequestParam String to) {
        log.info("[NaverCollector] 광고그룹 일별 전체 기간 수집 MQ 발행 from={} to={}", from, to);
        try {
            List<NaverAccountDto> accounts = mapper.selectNaverAccounts();
            Set<String> seen = new HashSet<>();
            int count = 0;
            for (NaverAccountDto account : accounts) {
                if ("admin".equals(account.getUserId())) continue;
                String cid = account.getAccountNaverCustomer();
                if (cid == null || cid.isBlank() || !seen.add(cid)) continue;
                producer.sendNaverAdGroupDailyRange(account.getUserId(), cid, from, to);
                count++;
            }
            return ResponseEntity.ok(Map.of("status", "ok", "message", from + "~" + to + " 광고그룹 일별 전체 수집 MQ 발행 완료 " + count + "건"));
        } catch (Exception e) {
            log.error("[NaverCollector] 광고그룹 일별 전체 기간 수집 MQ 발행 실패", e);
            return ResponseEntity.internalServerError().body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/adgroup-daily/{userId}")
    public ResponseEntity<Map<String, String>> collectAdGroupDailyByUser(@PathVariable String userId) {
        log.info("[NaverCollector] 광고그룹 일별 단일 수집 MQ 발행 userId={}", userId);
        try {
            NaverAccountDto account = findAccount(userId);
            if (account == null) return notFound(userId);
            producer.sendNaverAdGroupDaily(userId, account.getAccountNaverCustomer());
            return ResponseEntity.ok(Map.of("status", "ok", "message", userId + " 광고그룹 일별 수집 MQ 발행 완료"));
        } catch (Exception e) {
            log.error("[NaverCollector] 광고그룹 일별 수집 MQ 발행 실패", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/adgroup-daily/{userId}/range")
    public ResponseEntity<Map<String, String>> collectAdGroupDailyRange(
            @PathVariable String userId,
            @RequestParam String from,
            @RequestParam String to) {
        log.info("[NaverCollector] 광고그룹 일별 기간 수집 MQ 발행 userId={} from={} to={}", userId, from, to);
        try {
            NaverAccountDto account = findAccount(userId);
            if (account == null) return notFound(userId);
            producer.sendNaverAdGroupDailyRange(userId, account.getAccountNaverCustomer(), from, to);
            return ResponseEntity.ok(Map.of("status", "ok", "message", userId + " " + from + "~" + to + " 광고그룹 일별 수집 MQ 발행 완료"));
        } catch (Exception e) {
            log.error("[NaverCollector] 광고그룹 일별 기간 수집 MQ 발행 실패", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    // =====================================================================
    // 소재 일별
    // =====================================================================

    @PostMapping("/ad-daily")
    public ResponseEntity<Map<String, String>> collectAdDaily() {
        log.info("[NaverCollector] 소재 일별 전체 수집 시작");
        try {
            adDayJob.collect();
            return ResponseEntity.ok(Map.of("status", "ok", "message", "소재 일별 전체 수집 완료"));
        } catch (Exception e) {
            log.error("[NaverCollector] 소재 일별 수집 실패", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/ad-daily/range")
    public ResponseEntity<Map<String, String>> collectAdDailyAllRange(
            @RequestParam String from,
            @RequestParam String to) {
        log.info("[NaverCollector] 소재 일별 전체 기간 수집 MQ 발행 from={} to={}", from, to);
        try {
            List<NaverAccountDto> accounts = mapper.selectNaverAccounts();
            Set<String> seen = new HashSet<>();
            int count = 0;
            for (NaverAccountDto account : accounts) {
                if ("admin".equals(account.getUserId())) continue;
                String cid = account.getAccountNaverCustomer();
                if (cid == null || cid.isBlank() || !seen.add(cid)) continue;
                producer.sendNaverAdDailyRange(account.getUserId(), cid, from, to);
                count++;
            }
            return ResponseEntity.ok(Map.of("status", "ok", "message", from + "~" + to + " 소재 일별 전체 수집 MQ 발행 완료 " + count + "건"));
        } catch (Exception e) {
            log.error("[NaverCollector] 소재 일별 전체 기간 수집 MQ 발행 실패", e);
            return ResponseEntity.internalServerError().body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/ad-daily/{userId}")
    public ResponseEntity<Map<String, String>> collectAdDailyByUser(@PathVariable String userId) {
        log.info("[NaverCollector] 소재 일별 단일 수집 MQ 발행 userId={}", userId);
        try {
            NaverAccountDto account = findAccount(userId);
            if (account == null) return notFound(userId);
            producer.sendNaverAdDaily(userId, account.getAccountNaverCustomer());
            return ResponseEntity.ok(Map.of("status", "ok", "message", userId + " 소재 일별 수집 MQ 발행 완료"));
        } catch (Exception e) {
            log.error("[NaverCollector] 소재 일별 수집 MQ 발행 실패", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/ad-daily/{userId}/range")
    public ResponseEntity<Map<String, String>> collectAdDailyRange(
            @PathVariable String userId,
            @RequestParam String from,
            @RequestParam String to) {
        log.info("[NaverCollector] 소재 일별 기간 수집 MQ 발행 userId={} from={} to={}", userId, from, to);
        try {
            NaverAccountDto account = findAccount(userId);
            if (account == null) return notFound(userId);
            producer.sendNaverAdDailyRange(userId, account.getAccountNaverCustomer(), from, to);
            return ResponseEntity.ok(Map.of("status", "ok", "message", userId + " " + from + "~" + to + " 소재 일별 수집 MQ 발행 완료"));
        } catch (Exception e) {
            log.error("[NaverCollector] 소재 일별 기간 수집 MQ 발행 실패", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    // =====================================================================
    // 쇼핑소재 일별
    // =====================================================================

    @PostMapping("/shopping-daily")
    public ResponseEntity<Map<String, String>> collectShoppingDaily() {
        log.info("[NaverCollector] 쇼핑소재 일별 전체 수집 시작");
        try {
            shoppingDayJob.collect();
            return ResponseEntity.ok(Map.of("status", "ok", "message", "쇼핑소재 일별 전체 수집 완료"));
        } catch (Exception e) {
            log.error("[NaverCollector] 쇼핑소재 일별 수집 실패", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/shopping-daily/range")
    public ResponseEntity<Map<String, String>> collectShoppingDailyAllRange(
            @RequestParam String from,
            @RequestParam String to) {
        log.info("[NaverCollector] 쇼핑소재 일별 전체 기간 수집 MQ 발행 from={} to={}", from, to);
        try {
            List<NaverAccountDto> accounts = mapper.selectNaverAccounts();
            Set<String> seen = new HashSet<>();
            int count = 0;
            for (NaverAccountDto account : accounts) {
                if ("admin".equals(account.getUserId())) continue;
                String cid = account.getAccountNaverCustomer();
                if (cid == null || cid.isBlank() || !seen.add(cid)) continue;
                producer.sendNaverShoppingDailyRange(account.getUserId(), cid, from, to);
                count++;
            }
            return ResponseEntity.ok(Map.of("status", "ok", "message", from + "~" + to + " 쇼핑소재 일별 전체 수집 MQ 발행 완료 " + count + "건"));
        } catch (Exception e) {
            log.error("[NaverCollector] 쇼핑소재 일별 전체 기간 수집 MQ 발행 실패", e);
            return ResponseEntity.internalServerError().body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/shopping-daily/{userId}")
    public ResponseEntity<Map<String, String>> collectShoppingDailyByUser(@PathVariable String userId) {
        log.info("[NaverCollector] 쇼핑소재 일별 단일 수집 MQ 발행 userId={}", userId);
        try {
            NaverAccountDto account = findAccount(userId);
            if (account == null) return notFound(userId);
            producer.sendNaverShoppingDaily(userId, account.getAccountNaverCustomer());
            return ResponseEntity.ok(Map.of("status", "ok", "message", userId + " 쇼핑소재 일별 수집 MQ 발행 완료"));
        } catch (Exception e) {
            log.error("[NaverCollector] 쇼핑소재 일별 수집 MQ 발행 실패", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/shopping-daily/{userId}/range")
    public ResponseEntity<Map<String, String>> collectShoppingDailyRange(
            @PathVariable String userId,
            @RequestParam String from,
            @RequestParam String to) {
        log.info("[NaverCollector] 쇼핑소재 일별 기간 수집 MQ 발행 userId={} from={} to={}", userId, from, to);
        try {
            NaverAccountDto account = findAccount(userId);
            if (account == null) return notFound(userId);
            producer.sendNaverShoppingDailyRange(userId, account.getAccountNaverCustomer(), from, to);
            return ResponseEntity.ok(Map.of("status", "ok", "message", userId + " " + from + "~" + to + " 쇼핑소재 수집 MQ 발행 완료"));
        } catch (Exception e) {
            log.error("[NaverCollector] 쇼핑소재 일별 기간 수집 MQ 발행 실패", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    // =====================================================================
    // StateReport
    // =====================================================================

    @PostMapping("/state-report")
    public ResponseEntity<Map<String, String>> collectStateReport() {
        log.info("[NaverCollector] StateReport 전체 수집 MQ 발행 시작");
        try {
            List<NaverAccountDto> accounts = mapper.selectNaverAccounts();
            Set<String> seen = new HashSet<>();
            int count = 0;
            for (NaverAccountDto account : accounts) {
                if ("admin".equals(account.getUserId())) continue;
                String cid = account.getAccountNaverCustomer();
                if (cid == null || cid.isBlank() || !seen.add(cid)) continue;
                producer.sendNaverStateReport(account.getUserId(), cid);
                count++;
            }
            return ResponseEntity.ok(Map.of("status", "ok", "message", "StateReport MQ 발행 완료 " + count + "건"));
        } catch (Exception e) {
            log.error("[NaverCollector] StateReport MQ 발행 실패", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/state-report/range")
    public ResponseEntity<Map<String, String>> collectStateReportAllRange(
            @RequestParam String from,
            @RequestParam String to) {
        log.info("[NaverCollector] StateReport 전체 기간 수집 MQ 발행 from={} to={}", from, to);
        try {
            List<NaverAccountDto> accounts = mapper.selectNaverAccounts();
            Set<String> seen = new HashSet<>();
            int count = 0;
            for (NaverAccountDto account : accounts) {
                if ("admin".equals(account.getUserId())) continue;
                String cid = account.getAccountNaverCustomer();
                if (cid == null || cid.isBlank() || !seen.add(cid)) continue;
                producer.sendNaverStateReportRange(account.getUserId(), cid, from, to);
                count++;
            }
            return ResponseEntity.ok(Map.of("status", "ok", "message", from + "~" + to + " StateReport 전체 수집 MQ 발행 완료 " + count + "건"));
        } catch (Exception e) {
            log.error("[NaverCollector] StateReport 전체 기간 수집 MQ 발행 실패", e);
            return ResponseEntity.internalServerError().body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/state-report/{userId}")
    public ResponseEntity<Map<String, String>> collectStateReportByUser(@PathVariable String userId) {
        log.info("[NaverCollector] StateReport 단일 수집 MQ 발행 userId={}", userId);
        try {
            NaverAccountDto account = findAccount(userId);
            if (account == null) return notFound(userId);
            producer.sendNaverStateReport(userId, account.getAccountNaverCustomer());
            return ResponseEntity.ok(Map.of("status", "ok", "message", userId + " StateReport MQ 발행 완료"));
        } catch (Exception e) {
            log.error("[NaverCollector] StateReport MQ 발행 실패", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/state-report/{userId}/range")
    public ResponseEntity<Map<String, String>> collectStateReportRange(
            @PathVariable String userId,
            @RequestParam String from,
            @RequestParam String to) {
        log.info("[NaverCollector] StateReport 기간 수집 MQ 발행 userId={} from={} to={}", userId, from, to);
        try {
            NaverAccountDto account = findAccount(userId);
            if (account == null) return notFound(userId);
            producer.sendNaverStateReportRange(userId, account.getAccountNaverCustomer(), from, to);
            return ResponseEntity.ok(Map.of("status", "ok", "message", userId + " " + from + "~" + to + " StateReport MQ 발행 완료"));
        } catch (Exception e) {
            log.error("[NaverCollector] StateReport 기간 수집 MQ 발행 실패", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    // =====================================================================
    // 전환유형
    // =====================================================================

    @PostMapping("/conv-type")
    public ResponseEntity<Map<String, String>> collectConvType() {
        log.info("[NaverCollector] 전환유형 전체 수집 MQ 발행 시작");
        try {
            List<NaverAccountDto> accounts = mapper.selectNaverAccounts();
            Set<String> seen = new HashSet<>();
            int count = 0;
            for (NaverAccountDto account : accounts) {
                if ("admin".equals(account.getUserId())) continue;
                String cid = account.getAccountNaverCustomer();
                if (cid == null || cid.isBlank() || !seen.add(cid)) continue;
                producer.sendNaverConvType(account.getUserId(), cid);
                count++;
            }
            return ResponseEntity.ok(Map.of("status", "ok", "message", "전환유형 MQ 발행 완료 " + count + "건"));
        } catch (Exception e) {
            log.error("[NaverCollector] 전환유형 MQ 발행 실패", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/conv-type/range")
    public ResponseEntity<Map<String, String>> collectConvTypeAllRange(
            @RequestParam String from,
            @RequestParam String to) {
        log.info("[NaverCollector] 전환유형 전체 기간 수집 MQ 발행 from={} to={}", from, to);
        try {
            List<NaverAccountDto> accounts = mapper.selectNaverAccounts();
            Set<String> seen = new HashSet<>();
            int count = 0;
            for (NaverAccountDto account : accounts) {
                if ("admin".equals(account.getUserId())) continue;
                String cid = account.getAccountNaverCustomer();
                if (cid == null || cid.isBlank() || !seen.add(cid)) continue;
                producer.sendNaverConvTypeRange(account.getUserId(), cid, from, to);
                count++;
            }
            return ResponseEntity.ok(Map.of("status", "ok", "message", from + "~" + to + " 전환유형 전체 수집 MQ 발행 완료 " + count + "건"));
        } catch (Exception e) {
            log.error("[NaverCollector] 전환유형 전체 기간 수집 MQ 발행 실패", e);
            return ResponseEntity.internalServerError().body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/conv-type/{userId}")
    public ResponseEntity<Map<String, String>> collectConvTypeByUser(@PathVariable String userId) {
        log.info("[NaverCollector] 전환유형 단일 수집 MQ 발행 userId={}", userId);
        try {
            NaverAccountDto account = findAccount(userId);
            if (account == null) return notFound(userId);
            producer.sendNaverConvType(userId, account.getAccountNaverCustomer());
            return ResponseEntity.ok(Map.of("status", "ok", "message", userId + " 전환유형 MQ 발행 완료"));
        } catch (Exception e) {
            log.error("[NaverCollector] 전환유형 MQ 발행 실패", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/conv-type/{userId}/range")
    public ResponseEntity<Map<String, String>> collectConvTypeRange(
            @PathVariable String userId,
            @RequestParam String from,
            @RequestParam String to) {
        log.info("[NaverCollector] 전환유형 기간 수집 MQ 발행 userId={} from={} to={}", userId, from, to);
        try {
            NaverAccountDto account = findAccount(userId);
            if (account == null) return notFound(userId);
            producer.sendNaverConvTypeRange(userId, account.getAccountNaverCustomer(), from, to);
            return ResponseEntity.ok(Map.of("status", "ok", "message", userId + " " + from + "~" + to + " 전환유형 MQ 발행 완료"));
        } catch (Exception e) {
            log.error("[NaverCollector] 전환유형 기간 수집 MQ 발행 실패", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    // =====================================================================
    // GFA 마스터
    // =====================================================================

    @PostMapping("/gfa-master")
    public ResponseEntity<Map<String, String>> collectGfaMaster() {
        log.info("[NaverCollector] GFA 마스터 전체 수집 시작");
        try {
            gfaMasterJob.collect();
            return ResponseEntity.ok(Map.of("status", "ok", "message", "GFA 마스터 전체 수집 완료"));
        } catch (Exception e) {
            log.error("[NaverCollector] GFA 마스터 수집 실패", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    // =====================================================================
    // GFA 캠페인 일별
    // =====================================================================

    @PostMapping("/gfa-campaign-daily")
    public ResponseEntity<Map<String, String>> collectGfaCampaignDaily() {
        log.info("[NaverCollector] GFA 캠페인 일별 전체 수집 시작");
        try {
            gfaCampaignDayJob.collect();
            return ResponseEntity.ok(Map.of("status", "ok", "message", "GFA 캠페인 일별 전체 수집 완료"));
        } catch (Exception e) {
            log.error("[NaverCollector] GFA 캠페인 일별 수집 실패", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/gfa-campaign-daily/{userId}")
    public ResponseEntity<Map<String, String>> collectGfaCampaignDailyByUser(@PathVariable String userId) {
        log.info("[NaverCollector] GFA 캠페인 일별 단일 수집 MQ 발행 userId={}", userId);
        try {
            NaverGfaAccountDto account = gfaMapper.selectGfaAccounts().stream()
                    .filter(a -> userId.equals(a.getUserId()))
                    .findFirst().orElse(null);
            if (account == null) return notFound(userId);
            producer.sendNaverGfaCampaignDaily(userId);
            return ResponseEntity.ok(Map.of("status", "ok", "message", userId + " GFA 캠페인 일별 MQ 발행 완료"));
        } catch (Exception e) {
            log.error("[NaverCollector] GFA 캠페인 일별 MQ 발행 실패", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/gfa-campaign-daily/{userId}/range")
    public ResponseEntity<Map<String, String>> collectGfaCampaignDailyRange(
            @PathVariable String userId,
            @RequestParam String from,
            @RequestParam String to) {
        log.info("[NaverCollector] GFA 캠페인 일별 기간 수집 MQ 발행 userId={} from={} to={}", userId, from, to);
        try {
            NaverGfaAccountDto account = gfaMapper.selectGfaAccounts().stream()
                    .filter(a -> userId.equals(a.getUserId()))
                    .findFirst().orElse(null);
            if (account == null) return notFound(userId);
            producer.sendNaverGfaCampaignDailyRange(userId, from, to);
            return ResponseEntity.ok(Map.of("status", "ok", "message", userId + " " + from + "~" + to + " GFA 캠페인 일별 MQ 발행 완료"));
        } catch (Exception e) {
            log.error("[NaverCollector] GFA 캠페인 일별 기간 MQ 발행 실패", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    // =====================================================================
    // GFA 소재 일별
    // =====================================================================

    @PostMapping("/gfa-ad-daily")
    public ResponseEntity<Map<String, String>> collectGfaAdDaily() {
        log.info("[NaverCollector] GFA 소재 일별 전체 수집 시작");
        try {
            gfaAdDayJob.collect();
            return ResponseEntity.ok(Map.of("status", "ok", "message", "GFA 소재 일별 전체 수집 완료"));
        } catch (Exception e) {
            log.error("[NaverCollector] GFA 소재 일별 수집 실패", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/gfa-ad-daily/{userId}")
    public ResponseEntity<Map<String, String>> collectGfaAdDailyByUser(@PathVariable String userId) {
        log.info("[NaverCollector] GFA 소재 일별 단일 수집 MQ 발행 userId={}", userId);
        try {
            NaverGfaAccountDto account = gfaMapper.selectGfaAccounts().stream()
                    .filter(a -> userId.equals(a.getUserId()))
                    .findFirst().orElse(null);
            if (account == null) return notFound(userId);
            producer.sendNaverGfaAdDaily(userId);
            return ResponseEntity.ok(Map.of("status", "ok", "message", userId + " GFA 소재 일별 MQ 발행 완료"));
        } catch (Exception e) {
            log.error("[NaverCollector] GFA 소재 일별 MQ 발행 실패", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/gfa-ad-daily/{userId}/range")
    public ResponseEntity<Map<String, String>> collectGfaAdDailyRange(
            @PathVariable String userId,
            @RequestParam String from,
            @RequestParam String to) {
        log.info("[NaverCollector] GFA 소재 일별 기간 수집 MQ 발행 userId={} from={} to={}", userId, from, to);
        try {
            NaverGfaAccountDto account = gfaMapper.selectGfaAccounts().stream()
                    .filter(a -> userId.equals(a.getUserId()))
                    .findFirst().orElse(null);
            if (account == null) return notFound(userId);
            producer.sendNaverGfaAdDailyRange(userId, from, to);
            return ResponseEntity.ok(Map.of("status", "ok", "message", userId + " " + from + "~" + to + " GFA 소재 일별 MQ 발행 완료"));
        } catch (Exception e) {
            log.error("[NaverCollector] GFA 소재 일별 기간 MQ 발행 실패", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    // =====================================================================
    // GFA 광고그룹 일별
    // =====================================================================

    @PostMapping("/gfa-adgroup-daily")
    public ResponseEntity<Map<String, String>> collectGfaAdgroupDaily() {
        log.info("[NaverCollector] GFA 광고그룹 일별 전체 수집 시작");
        try {
            gfaAdgroupDayJob.collect();
            return ResponseEntity.ok(Map.of("status", "ok", "message", "GFA 광고그룹 일별 전체 수집 완료"));
        } catch (Exception e) {
            log.error("[NaverCollector] GFA 광고그룹 일별 수집 실패", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/gfa-adgroup-daily/{userId}")
    public ResponseEntity<Map<String, String>> collectGfaAdgroupDailyByUser(@PathVariable String userId) {
        log.info("[NaverCollector] GFA 광고그룹 일별 단일 수집 MQ 발행 userId={}", userId);
        try {
            NaverGfaAccountDto account = gfaMapper.selectGfaAccounts().stream()
                    .filter(a -> userId.equals(a.getUserId()))
                    .findFirst().orElse(null);
            if (account == null) return notFound(userId);
            producer.sendNaverGfaAdgroupDaily(userId);
            return ResponseEntity.ok(Map.of("status", "ok", "message", userId + " GFA 광고그룹 일별 MQ 발행 완료"));
        } catch (Exception e) {
            log.error("[NaverCollector] GFA 광고그룹 일별 MQ 발행 실패", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/gfa-adgroup-daily/{userId}/range")
    public ResponseEntity<Map<String, String>> collectGfaAdgroupDailyRange(
            @PathVariable String userId,
            @RequestParam String from,
            @RequestParam String to) {
        log.info("[NaverCollector] GFA 광고그룹 일별 기간 수집 MQ 발행 userId={} from={} to={}", userId, from, to);
        try {
            NaverGfaAccountDto account = gfaMapper.selectGfaAccounts().stream()
                    .filter(a -> userId.equals(a.getUserId()))
                    .findFirst().orElse(null);
            if (account == null) return notFound(userId);
            producer.sendNaverGfaAdgroupDailyRange(userId, from, to);
            return ResponseEntity.ok(Map.of("status", "ok", "message", userId + " " + from + "~" + to + " GFA 광고그룹 일별 MQ 발행 완료"));
        } catch (Exception e) {
            log.error("[NaverCollector] GFA 광고그룹 일별 기간 MQ 발행 실패", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    // =====================================================================
    // GFA 예산 알람
    // =====================================================================

    @PostMapping("/gfa-budget-alarm")
    public ResponseEntity<Map<String, String>> collectGfaBudgetAlarm() {
        log.info("[NaverCollector] GFA 예산 알람 전체 수집 시작");
        try {
            gfaBudgetAlarmJob.collect();
            return ResponseEntity.ok(Map.of("status", "ok", "message", "GFA 예산 알람 전체 수집 완료"));
        } catch (Exception e) {
            log.error("[NaverCollector] GFA 예산 알람 수집 실패", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/gfa-budget-alarm/{userId}")
    public ResponseEntity<Map<String, String>> collectGfaBudgetAlarmByUser(@PathVariable String userId) {
        log.info("[NaverCollector] GFA 예산 알람 단일 수집 MQ 발행 userId={}", userId);
        try {
            NaverGfaAccountDto account = gfaMapper.selectGfaAccounts().stream()
                    .filter(a -> userId.equals(a.getUserId()))
                    .findFirst().orElse(null);
            if (account == null) return notFound(userId);
            producer.sendNaverGfaBudgetAlarm(userId);
            return ResponseEntity.ok(Map.of("status", "ok", "message", userId + " GFA 예산 알람 MQ 발행 완료"));
        } catch (Exception e) {
            log.error("[NaverCollector] GFA 예산 알람 MQ 발행 실패", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    // =====================================================================
    // GFA 전환유형
    // =====================================================================

    @PostMapping("/gfa-conv-type")
    public ResponseEntity<Map<String, String>> collectGfaConvType() {
        log.info("[NaverCollector] GFA 전환유형 전체 수집 시작");
        try {
            gfaConvTypeJob.collect();
            return ResponseEntity.ok(Map.of("status", "ok", "message", "GFA 전환유형 전체 수집 완료"));
        } catch (Exception e) {
            log.error("[NaverCollector] GFA 전환유형 수집 실패", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/gfa-conv-type/{userId}")
    public ResponseEntity<Map<String, String>> collectGfaConvTypeByUser(@PathVariable String userId) {
        log.info("[NaverCollector] GFA 전환유형 단일 수집 MQ 발행 userId={}", userId);
        try {
            NaverGfaAccountDto account = gfaMapper.selectGfaAccounts().stream()
                    .filter(a -> userId.equals(a.getUserId()))
                    .findFirst().orElse(null);
            if (account == null) return notFound(userId);
            producer.sendNaverGfaConvType(userId);
            return ResponseEntity.ok(Map.of("status", "ok", "message", userId + " GFA 전환유형 MQ 발행 완료"));
        } catch (Exception e) {
            log.error("[NaverCollector] GFA 전환유형 MQ 발행 실패", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/gfa-conv-type/{userId}/range")
    public ResponseEntity<Map<String, String>> collectGfaConvTypeRange(
            @PathVariable String userId,
            @RequestParam String from,
            @RequestParam String to) {
        log.info("[NaverCollector] GFA 전환유형 기간 수집 MQ 발행 userId={} from={} to={}", userId, from, to);
        try {
            NaverGfaAccountDto account = gfaMapper.selectGfaAccounts().stream()
                    .filter(a -> userId.equals(a.getUserId()))
                    .findFirst().orElse(null);
            if (account == null) return notFound(userId);
            producer.sendNaverGfaConvTypeRange(userId, from, to);
            return ResponseEntity.ok(Map.of("status", "ok", "message", userId + " " + from + "~" + to + " GFA 전환유형 MQ 발행 완료"));
        } catch (Exception e) {
            log.error("[NaverCollector] GFA 전환유형 기간 MQ 발행 실패", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/gfa-master/{userId}")
    public ResponseEntity<Map<String, String>> collectGfaMasterByUser(@PathVariable String userId) {
        log.info("[NaverCollector] GFA 마스터 단일 수집 MQ 발행 userId={}", userId);
        try {
            NaverGfaAccountDto account = gfaMapper.selectGfaAccounts().stream()
                    .filter(a -> userId.equals(a.getUserId()))
                    .findFirst().orElse(null);
            if (account == null) return notFound(userId);
            producer.sendNaverGfaMaster(userId);
            return ResponseEntity.ok(Map.of("status", "ok", "message", userId + " GFA 마스터 수집 MQ 발행 완료"));
        } catch (Exception e) {
            log.error("[NaverCollector] GFA 마스터 수집 MQ 발행 실패", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }
}
