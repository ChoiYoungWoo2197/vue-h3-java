package com.h3.h3_java.batch.scheduler;

import com.h3.h3_java.media.google.dto.GoogleAccountDto;
import com.h3.h3_java.media.kakao.dto.KakaoMoAccountDto;
import com.h3.h3_java.media.kakao.dto.KakaoSaAccountDto;
import com.h3.h3_java.media.naver.dto.NaverAccountDto;
import com.h3.h3_java.media.naver.dto.NaverGfaAccountDto;
import com.h3.h3_java.queue.producer.CollectorProducer;
import com.h3.h3_java.raw.mongo.AccountMongoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class CollectorScheduler {

    private final AccountMongoService accountMongo;
    private final CollectorProducer   producer;

    // 매일 오전 7시 00분 - 네이버 SA 예산 알람 (SA 수집 완료 후)
    @Scheduled(cron = "0 0 7 * * *", zone = "Asia/Seoul")
    public void scheduleNaverSaBudgetAlarm() {
        log.info("[SCHEDULER] 네이버 SA 예산 알람 시작");
        List<NaverAccountDto> accounts = accountMongo.findNaverAccountDtos();
        int count = 0;
        for (NaverAccountDto account : accounts) {
            if ("admin".equals(account.getUserId()) || "dydrp123".equals(account.getUserId())) continue;
            producer.sendNaverSaBudgetAlarm(account.getUserId());
            count++;
        }
        log.info("[SCHEDULER] 네이버 SA 예산 알람 메시지 발행 완료 총={}건", count);
    }

    // 매일 오전 5시 00분 - GFA 전환유형 수집
    @Scheduled(cron = "0 0 5 * * *", zone = "Asia/Seoul")
    public void scheduleNaverGfaConvType() {
        log.info("[SCHEDULER] 네이버 GFA 전환유형 수집 시작");
        List<NaverGfaAccountDto> accounts = accountMongo.findGfaAccountDtos();
        Set<String> seen = new HashSet<>();
        int count = 0;
        for (NaverGfaAccountDto account : accounts) {
            String advkey = account.getAccountGfa();
            if (advkey == null || advkey.isBlank() || !seen.add(advkey)) continue;
            producer.sendNaverGfaConvType(account.getUserId());
            count++;
        }
        log.info("[SCHEDULER] 네이버 GFA 전환유형 메시지 발행 완료 총={}건", count);
    }

    // 매일 오전 4시 30분 - GFA 소재 일별 수집
    @Scheduled(cron = "0 30 4 * * *", zone = "Asia/Seoul")
    public void scheduleNaverGfaAdDaily() {
        log.info("[SCHEDULER] 네이버 GFA 소재 일별 수집 시작");
        List<NaverGfaAccountDto> accounts = accountMongo.findGfaAccountDtos();
        Set<String> seen = new HashSet<>();
        int count = 0;
        for (NaverGfaAccountDto account : accounts) {
            String advkey = account.getAccountGfa();
            if (advkey == null || advkey.isBlank() || !seen.add(advkey)) continue;
            producer.sendNaverGfaAdDaily(account.getUserId());
            count++;
        }
        log.info("[SCHEDULER] 네이버 GFA 소재 일별 메시지 발행 완료 총={}건", count);
    }

    // 매일 오전 3시 30분 - GFA 광고그룹 일별 수집
    @Scheduled(cron = "0 30 3 * * *", zone = "Asia/Seoul")
    public void scheduleNaverGfaAdgroupDaily() {
        log.info("[SCHEDULER] 네이버 GFA 광고그룹 일별 수집 시작");
        List<NaverGfaAccountDto> accounts = accountMongo.findGfaAccountDtos();
        Set<String> seen = new HashSet<>();
        int count = 0;
        for (NaverGfaAccountDto account : accounts) {
            String advkey = account.getAccountGfa();
            if (advkey == null || advkey.isBlank() || !seen.add(advkey)) continue;
            producer.sendNaverGfaAdgroupDaily(account.getUserId());
            count++;
        }
        log.info("[SCHEDULER] 네이버 GFA 광고그룹 일별 메시지 발행 완료 총={}건", count);
    }

    // 매일 오전 4시 - GFA 예산 알람 (GFA 캠페인 일별 수집 완료 후)
    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    public void scheduleNaverGfaBudgetAlarm() {
        log.info("[SCHEDULER] 네이버 GFA 예산 알람 시작");
        List<NaverGfaAccountDto> accounts = accountMongo.findGfaAccountDtos();
        Set<String> seen = new HashSet<>();
        int count = 0;
        for (NaverGfaAccountDto account : accounts) {
            String advkey = account.getAccountGfa();
            if (advkey == null || advkey.isBlank() || !seen.add(advkey)) continue;
            producer.sendNaverGfaBudgetAlarm(account.getUserId());
            count++;
        }
        log.info("[SCHEDULER] 네이버 GFA 예산 알람 메시지 발행 완료 총={}건", count);
    }

    // 매일 오전 3시 - GFA 캠페인 일별 수집
    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Seoul")
    public void scheduleNaverGfaCampaignDaily() {
        log.info("[SCHEDULER] 네이버 GFA 캠페인 일별 수집 시작");
        List<NaverGfaAccountDto> accounts = accountMongo.findGfaAccountDtos();
        Set<String> seen = new HashSet<>();
        int count = 0;
        for (NaverGfaAccountDto account : accounts) {
            String advkey = account.getAccountGfa();
            if (advkey == null || advkey.isBlank() || !seen.add(advkey)) continue;
            producer.sendNaverGfaCampaignDaily(account.getUserId());
            count++;
        }
        log.info("[SCHEDULER] 네이버 GFA 캠페인 일별 메시지 발행 완료 총={}건", count);
    }

    // 매일 새벽 1시 - GFA 마스터 수집 (SA 마스터보다 먼저)
    @Scheduled(cron = "0 0 1 * * *", zone = "Asia/Seoul")
    public void scheduleNaverGfaMaster() {
        log.info("[SCHEDULER] 네이버 GFA 마스터 수집 시작");
        List<NaverGfaAccountDto> accounts = accountMongo.findGfaAccountDtos();
        Set<String> seen = new HashSet<>();
        int count = 0;
        for (NaverGfaAccountDto account : accounts) {
            String advkey = account.getAccountGfa();
            if (advkey == null || advkey.isBlank() || !seen.add(advkey)) continue;
            producer.sendNaverGfaMaster(account.getUserId());
            count++;
        }
        log.info("[SCHEDULER] 네이버 GFA 마스터 메시지 발행 완료 총={}건", count);
    }

    // 매일 새벽 2시 - 마스터 수집
    @Scheduled(cron = "0 0 2 * * *", zone = "Asia/Seoul")
    public void scheduleNaverMaster() {
        log.info("[SCHEDULER] 네이버 마스터 수집 시작");
        List<NaverAccountDto> accounts = accountMongo.findNaverAccountDtos();
        Set<String> seen = new HashSet<>();
        int count = 0;
        for (NaverAccountDto account : accounts) {
            if ("admin".equals(account.getUserId())) continue;
            String cid = account.getAccountNaverCustomer();
            if (cid == null || cid.isBlank() || !seen.add(cid)) continue;
            producer.sendNaverMaster(account.getUserId(), cid, false);
            count++;
        }
        log.info("[SCHEDULER] 네이버 마스터 메시지 발행 완료 총={}건", count);
    }

    // 매일 새벽 3시 - 캠페인 일별 수집 (마스터 완료 후)
    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Seoul")
    public void scheduleNaverCampaignDaily() {
        log.info("[SCHEDULER] 네이버 캠페인 일별 수집 시작");
        List<NaverAccountDto> accounts = accountMongo.findNaverAccountDtos();
        Set<String> seen = new HashSet<>();
        int count = 0;
        for (NaverAccountDto account : accounts) {
            if ("admin".equals(account.getUserId())) continue;
            String cid = account.getAccountNaverCustomer();
            if (cid == null || cid.isBlank() || !seen.add(cid)) continue;
            producer.sendNaverCampaignDaily(account.getUserId(), cid);
            count++;
        }
        log.info("[SCHEDULER] 네이버 캠페인 일별 메시지 발행 완료 총={}건", count);
    }

    // 매일 새벽 5시 30분 - 전환유형 수집
    @Scheduled(cron = "0 30 5 * * *", zone = "Asia/Seoul")
    public void scheduleNaverConvType() {
        log.info("[SCHEDULER] 네이버 전환유형 수집 시작");
        List<NaverAccountDto> accounts = accountMongo.findNaverAccountDtos();
        Set<String> seen = new HashSet<>();
        int count = 0;
        for (NaverAccountDto account : accounts) {
            if ("admin".equals(account.getUserId())) continue;
            String cid = account.getAccountNaverCustomer();
            if (cid == null || cid.isBlank() || !seen.add(cid)) continue;
            producer.sendNaverConvType(account.getUserId(), cid);
            count++;
        }
        log.info("[SCHEDULER] 네이버 전환유형 메시지 발행 완료 총={}건", count);
    }

    // 매일 새벽 5시 - 키워드·타겟 일별 수집 (StateReport)
    @Scheduled(cron = "0 0 5 * * *", zone = "Asia/Seoul")
    public void scheduleNaverStateReport() {
        log.info("[SCHEDULER] 네이버 StateReport 수집 시작");
        List<NaverAccountDto> accounts = accountMongo.findNaverAccountDtos();
        Set<String> seen = new HashSet<>();
        int count = 0;
        for (NaverAccountDto account : accounts) {
            if ("admin".equals(account.getUserId())) continue;
            String cid = account.getAccountNaverCustomer();
            if (cid == null || cid.isBlank() || !seen.add(cid)) continue;
            producer.sendNaverStateReport(account.getUserId(), cid);
            count++;
        }
        log.info("[SCHEDULER] 네이버 StateReport 메시지 발행 완료 총={}건", count);
    }

    // 매일 새벽 4시 45분 - 쇼핑소재 일별 수집
    @Scheduled(cron = "0 45 4 * * *", zone = "Asia/Seoul")
    public void scheduleNaverShoppingDaily() {
        log.info("[SCHEDULER] 네이버 쇼핑소재 일별 수집 시작");
        List<NaverAccountDto> accounts = accountMongo.findNaverAccountDtos();
        Set<String> seen = new HashSet<>();
        int count = 0;
        for (NaverAccountDto account : accounts) {
            if ("admin".equals(account.getUserId())) continue;
            String cid = account.getAccountNaverCustomer();
            if (cid == null || cid.isBlank() || !seen.add(cid)) continue;
            producer.sendNaverShoppingDaily(account.getUserId(), cid);
            count++;
        }
        log.info("[SCHEDULER] 네이버 쇼핑소재 일별 메시지 발행 완료 총={}건", count);
    }

    // 매일 새벽 4시 30분 - 소재 일별 수집
    @Scheduled(cron = "0 30 4 * * *", zone = "Asia/Seoul")
    public void scheduleNaverAdDaily() {
        log.info("[SCHEDULER] 네이버 소재 일별 수집 시작");
        List<NaverAccountDto> accounts = accountMongo.findNaverAccountDtos();
        Set<String> seen = new HashSet<>();
        int count = 0;
        for (NaverAccountDto account : accounts) {
            if ("admin".equals(account.getUserId())) continue;
            String cid = account.getAccountNaverCustomer();
            if (cid == null || cid.isBlank() || !seen.add(cid)) continue;
            producer.sendNaverAdDaily(account.getUserId(), cid);
            count++;
        }
        log.info("[SCHEDULER] 네이버 소재 일별 메시지 발행 완료 총={}건", count);
    }

    // 매일 새벽 4시 - 광고그룹 일별 수집
    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    public void scheduleNaverAdGroupDaily() {
        log.info("[SCHEDULER] 네이버 광고그룹 일별 수집 시작");
        List<NaverAccountDto> accounts = accountMongo.findNaverAccountDtos();
        Set<String> seen = new HashSet<>();
        int count = 0;
        for (NaverAccountDto account : accounts) {
            if ("admin".equals(account.getUserId())) continue;
            String cid = account.getAccountNaverCustomer();
            if (cid == null || cid.isBlank() || !seen.add(cid)) continue;
            producer.sendNaverAdGroupDaily(account.getUserId(), cid);
            count++;
        }
        log.info("[SCHEDULER] 네이버 광고그룹 일별 메시지 발행 완료 총={}건", count);
    }

    // 매일 새벽 3시 30분 - 캠페인 시간별 수집
    @Scheduled(cron = "0 30 3 * * *", zone = "Asia/Seoul")
    public void scheduleNaverCampaignHour() {
        log.info("[SCHEDULER] 네이버 캠페인 시간별 수집 시작");
        List<NaverAccountDto> accounts = accountMongo.findNaverAccountDtos();
        Set<String> seen = new HashSet<>();
        int count = 0;
        for (NaverAccountDto account : accounts) {
            if ("admin".equals(account.getUserId())) continue;
            String cid = account.getAccountNaverCustomer();
            if (cid == null || cid.isBlank() || !seen.add(cid)) continue;
            producer.sendNaverCampaignHour(account.getUserId(), cid);
            count++;
        }
        log.info("[SCHEDULER] 네이버 캠페인 시간별 메시지 발행 완료 총={}건", count);
    }

    // ── Kakao SA 스케줄 ────────────────────────────────────────────────────────

    // 매일 오전 3시 - Kakao SA 마스터 수집
    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Seoul")
    public void scheduleKakaoSaMaster() {
        log.info("[SCHEDULER] 카카오SA 마스터 수집 시작");
        List<KakaoSaAccountDto> accounts = accountMongo.findKakaoSaAccountDtos();
        Set<String> seen = new HashSet<>();
        int count = 0;
        for (KakaoSaAccountDto account : accounts) {
            String advkey = account.getAccountKakaosa();
            if (advkey == null || advkey.isBlank() || !seen.add(advkey)) continue;
            producer.sendKakaoSaMaster(account.getUserId());
            count++;
        }
        log.info("[SCHEDULER] 카카오SA 마스터 메시지 발행 완료 총={}건", count);
    }

    // 매일 오전 4시 - Kakao SA 캠페인 일별 수집
    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    public void scheduleKakaoSaCampaignDaily() {
        log.info("[SCHEDULER] 카카오SA 캠페인 일별 수집 시작");
        List<KakaoSaAccountDto> accounts = accountMongo.findKakaoSaAccountDtos();
        Set<String> seen = new HashSet<>();
        int count = 0;
        for (KakaoSaAccountDto account : accounts) {
            String advkey = account.getAccountKakaosa();
            if (advkey == null || advkey.isBlank() || !seen.add(advkey)) continue;
            producer.sendKakaoSaCampaignDaily(account.getUserId());
            count++;
        }
        log.info("[SCHEDULER] 카카오SA 캠페인 일별 메시지 발행 완료 총={}건", count);
    }

    // 매일 오전 4시 30분 - Kakao SA 캠페인 시간별 수집
    @Scheduled(cron = "0 30 4 * * *", zone = "Asia/Seoul")
    public void scheduleKakaoSaCampaignHour() {
        log.info("[SCHEDULER] 카카오SA 캠페인 시간별 수집 시작");
        List<KakaoSaAccountDto> accounts = accountMongo.findKakaoSaAccountDtos();
        Set<String> seen = new HashSet<>();
        int count = 0;
        for (KakaoSaAccountDto account : accounts) {
            String advkey = account.getAccountKakaosa();
            if (advkey == null || advkey.isBlank() || !seen.add(advkey)) continue;
            producer.sendKakaoSaCampaignHour(account.getUserId());
            count++;
        }
        log.info("[SCHEDULER] 카카오SA 캠페인 시간별 메시지 발행 완료 총={}건", count);
    }

    // 매일 오전 5시 - Kakao SA 광고그룹 일별 수집
    @Scheduled(cron = "0 0 5 * * *", zone = "Asia/Seoul")
    public void scheduleKakaoSaAdGroupDaily() {
        log.info("[SCHEDULER] 카카오SA 광고그룹 일별 수집 시작");
        List<KakaoSaAccountDto> accounts = accountMongo.findKakaoSaAccountDtos();
        Set<String> seen = new HashSet<>();
        int count = 0;
        for (KakaoSaAccountDto account : accounts) {
            String advkey = account.getAccountKakaosa();
            if (advkey == null || advkey.isBlank() || !seen.add(advkey)) continue;
            producer.sendKakaoSaAdGroupDaily(account.getUserId());
            count++;
        }
        log.info("[SCHEDULER] 카카오SA 광고그룹 일별 메시지 발행 완료 총={}건", count);
    }

    // 매일 오전 5시 30분 - Kakao SA 키워드 일별 수집
    @Scheduled(cron = "0 30 5 * * *", zone = "Asia/Seoul")
    public void scheduleKakaoSaKeywordDaily() {
        log.info("[SCHEDULER] 카카오SA 키워드 일별 수집 시작");
        List<KakaoSaAccountDto> accounts = accountMongo.findKakaoSaAccountDtos();
        Set<String> seen = new HashSet<>();
        int count = 0;
        for (KakaoSaAccountDto account : accounts) {
            String advkey = account.getAccountKakaosa();
            if (advkey == null || advkey.isBlank() || !seen.add(advkey)) continue;
            producer.sendKakaoSaKeywordDaily(account.getUserId());
            count++;
        }
        log.info("[SCHEDULER] 카카오SA 키워드 일별 메시지 발행 완료 총={}건", count);
    }

    // 매일 오전 6시 - Kakao SA 소재 일별 수집
    @Scheduled(cron = "0 0 6 * * *", zone = "Asia/Seoul")
    public void scheduleKakaoSaAdDaily() {
        log.info("[SCHEDULER] 카카오SA 소재 일별 수집 시작");
        List<KakaoSaAccountDto> accounts = accountMongo.findKakaoSaAccountDtos();
        Set<String> seen = new HashSet<>();
        int count = 0;
        for (KakaoSaAccountDto account : accounts) {
            String advkey = account.getAccountKakaosa();
            if (advkey == null || advkey.isBlank() || !seen.add(advkey)) continue;
            producer.sendKakaoSaAdDaily(account.getUserId());
            count++;
        }
        log.info("[SCHEDULER] 카카오SA 소재 일별 메시지 발행 완료 총={}건", count);
    }

    // 매일 오전 6시 30분 - Kakao SA 예산 알람
    @Scheduled(cron = "0 30 6 * * *", zone = "Asia/Seoul")
    public void scheduleKakaoSaBudgetAlarm() {
        log.info("[SCHEDULER] 카카오SA 예산 알람 시작");
        List<KakaoSaAccountDto> accounts = accountMongo.findKakaoSaAccountDtos();
        Set<String> seen = new HashSet<>();
        int count = 0;
        for (KakaoSaAccountDto account : accounts) {
            String advkey = account.getAccountKakaosa();
            if (advkey == null || advkey.isBlank() || !seen.add(advkey)) continue;
            producer.sendKakaoSaBudgetAlarm(account.getUserId());
            count++;
        }
        log.info("[SCHEDULER] 카카오SA 예산 알람 메시지 발행 완료 총={}건", count);
    }

    // ── Kakao MO 스케줄 ────────────────────────────────────────────────────────

    // 매일 오전 5시 30분 - Kakao MO 마스터 수집
    @Scheduled(cron = "0 30 5 * * *", zone = "Asia/Seoul")
    public void scheduleKakaoMoMaster() {
        log.info("[SCHEDULER] 카카오MO 마스터 수집 시작");
        List<KakaoMoAccountDto> accounts = accountMongo.findKakaoMoAccountDtos();
        Set<String> seen = new HashSet<>();
        int count = 0;
        for (KakaoMoAccountDto account : accounts) {
            String advkey = account.getAccountKakaomoment();
            if (advkey == null || advkey.isBlank() || !seen.add(advkey)) continue;
            producer.sendKakaoMoMaster(account.getUserId());
            count++;
        }
        log.info("[SCHEDULER] 카카오MO 마스터 메시지 발행 완료 총={}건", count);
    }

    // 매일 오전 6시 30분 - Kakao MO 캠페인 일별 수집
    @Scheduled(cron = "0 30 6 * * *", zone = "Asia/Seoul")
    public void scheduleKakaoMoCampaignDaily() {
        log.info("[SCHEDULER] 카카오MO 캠페인 일별 수집 시작");
        List<KakaoMoAccountDto> accounts = accountMongo.findKakaoMoAccountDtos();
        Set<String> seen = new HashSet<>();
        int count = 0;
        for (KakaoMoAccountDto account : accounts) {
            String advkey = account.getAccountKakaomoment();
            if (advkey == null || advkey.isBlank() || !seen.add(advkey)) continue;
            producer.sendKakaoMoCampaignDaily(account.getUserId());
            count++;
        }
        log.info("[SCHEDULER] 카카오MO 캠페인 일별 메시지 발행 완료 총={}건", count);
    }

    // 매일 오전 7시 - Kakao MO 캠페인 시간별 수집
    @Scheduled(cron = "0 0 7 * * *", zone = "Asia/Seoul")
    public void scheduleKakaoMoCampaignHour() {
        log.info("[SCHEDULER] 카카오MO 캠페인 시간별 수집 시작");
        List<KakaoMoAccountDto> accounts = accountMongo.findKakaoMoAccountDtos();
        Set<String> seen = new HashSet<>();
        int count = 0;
        for (KakaoMoAccountDto account : accounts) {
            String advkey = account.getAccountKakaomoment();
            if (advkey == null || advkey.isBlank() || !seen.add(advkey)) continue;
            producer.sendKakaoMoCampaignHour(account.getUserId());
            count++;
        }
        log.info("[SCHEDULER] 카카오MO 캠페인 시간별 메시지 발행 완료 총={}건", count);
    }

    // 매일 오전 7시 30분 - Kakao MO 광고그룹 일별 수집
    @Scheduled(cron = "0 30 7 * * *", zone = "Asia/Seoul")
    public void scheduleKakaoMoAdGroupDaily() {
        log.info("[SCHEDULER] 카카오MO 광고그룹 일별 수집 시작");
        List<KakaoMoAccountDto> accounts = accountMongo.findKakaoMoAccountDtos();
        Set<String> seen = new HashSet<>();
        int count = 0;
        for (KakaoMoAccountDto account : accounts) {
            String advkey = account.getAccountKakaomoment();
            if (advkey == null || advkey.isBlank() || !seen.add(advkey)) continue;
            producer.sendKakaoMoAdGroupDaily(account.getUserId());
            count++;
        }
        log.info("[SCHEDULER] 카카오MO 광고그룹 일별 메시지 발행 완료 총={}건", count);
    }

    // 매일 오전 8시 - Kakao MO 소재 일별 수집
    @Scheduled(cron = "0 0 8 * * *", zone = "Asia/Seoul")
    public void scheduleKakaoMoAdDaily() {
        log.info("[SCHEDULER] 카카오MO 소재 일별 수집 시작");
        List<KakaoMoAccountDto> accounts = accountMongo.findKakaoMoAccountDtos();
        Set<String> seen = new HashSet<>();
        int count = 0;
        for (KakaoMoAccountDto account : accounts) {
            String advkey = account.getAccountKakaomoment();
            if (advkey == null || advkey.isBlank() || !seen.add(advkey)) continue;
            producer.sendKakaoMoAdDaily(account.getUserId());
            count++;
        }
        log.info("[SCHEDULER] 카카오MO 소재 일별 메시지 발행 완료 총={}건", count);
    }

    // 매일 오전 8시 30분 - Kakao MO 예산 알람
    @Scheduled(cron = "0 30 8 * * *", zone = "Asia/Seoul")
    public void scheduleKakaoMoBudgetAlarm() {
        log.info("[SCHEDULER] 카카오MO 예산 알람 시작");
        List<KakaoMoAccountDto> accounts = accountMongo.findKakaoMoAccountDtos();
        Set<String> seen = new HashSet<>();
        int count = 0;
        for (KakaoMoAccountDto account : accounts) {
            String advkey = account.getAccountKakaomoment();
            if (advkey == null || advkey.isBlank() || !seen.add(advkey)) continue;
            producer.sendKakaoMoBudgetAlarm(account.getUserId());
            count++;
        }
        log.info("[SCHEDULER] 카카오MO 예산 알람 메시지 발행 완료 총={}건", count);
    }

    // ── Google 스케줄 ──────────────────────────────────────────────────────────

    // 매일 오전 5시 - Google 마스터 수집
    @Scheduled(cron = "0 0 5 * * *", zone = "Asia/Seoul")
    public void scheduleGoogleMaster() {
        log.info("[SCHEDULER] 구글 마스터 수집 시작");
        List<GoogleAccountDto> accounts = accountMongo.findGoogleAccountDtos();
        Set<String> seen = new HashSet<>();
        int count = 0;
        for (GoogleAccountDto account : accounts) {
            String advkey = account.getAccountGoogle();
            if (advkey == null || advkey.isBlank() || !seen.add(advkey)) continue;
            producer.sendGoogleMaster(account.getUserId());
            count++;
        }
        log.info("[SCHEDULER] 구글 마스터 메시지 발행 완료 총={}건", count);
    }

    // 매일 오전 6시 - Google 캠페인 일별 수집
    @Scheduled(cron = "0 0 6 * * *", zone = "Asia/Seoul")
    public void scheduleGoogleCampaignDaily() {
        log.info("[SCHEDULER] 구글 캠페인 일별 수집 시작");
        List<GoogleAccountDto> accounts = accountMongo.findGoogleAccountDtos();
        Set<String> seen = new HashSet<>();
        int count = 0;
        for (GoogleAccountDto account : accounts) {
            String advkey = account.getAccountGoogle();
            if (advkey == null || advkey.isBlank() || !seen.add(advkey)) continue;
            producer.sendGoogleCampaignDaily(account.getUserId());
            count++;
        }
        log.info("[SCHEDULER] 구글 캠페인 일별 메시지 발행 완료 총={}건", count);
    }

    // 매일 오전 6시 30분 - Google 캠페인 시간별 수집
    @Scheduled(cron = "0 30 6 * * *", zone = "Asia/Seoul")
    public void scheduleGoogleCampaignHour() {
        log.info("[SCHEDULER] 구글 캠페인 시간별 수집 시작");
        List<GoogleAccountDto> accounts = accountMongo.findGoogleAccountDtos();
        Set<String> seen = new HashSet<>();
        int count = 0;
        for (GoogleAccountDto account : accounts) {
            String advkey = account.getAccountGoogle();
            if (advkey == null || advkey.isBlank() || !seen.add(advkey)) continue;
            producer.sendGoogleCampaignHour(account.getUserId());
            count++;
        }
        log.info("[SCHEDULER] 구글 캠페인 시간별 메시지 발행 완료 총={}건", count);
    }

    // 매일 오전 7시 - Google 광고그룹 일별 수집
    @Scheduled(cron = "0 0 7 * * *", zone = "Asia/Seoul")
    public void scheduleGoogleAdGroupDaily() {
        log.info("[SCHEDULER] 구글 광고그룹 일별 수집 시작");
        List<GoogleAccountDto> accounts = accountMongo.findGoogleAccountDtos();
        Set<String> seen = new HashSet<>();
        int count = 0;
        for (GoogleAccountDto account : accounts) {
            String advkey = account.getAccountGoogle();
            if (advkey == null || advkey.isBlank() || !seen.add(advkey)) continue;
            producer.sendGoogleAdGroupDaily(account.getUserId());
            count++;
        }
        log.info("[SCHEDULER] 구글 광고그룹 일별 메시지 발행 완료 총={}건", count);
    }

    // 매일 오전 7시 30분 - Google 소재 일별 수집
    @Scheduled(cron = "0 30 7 * * *", zone = "Asia/Seoul")
    public void scheduleGoogleAdDaily() {
        log.info("[SCHEDULER] 구글 소재 일별 수집 시작");
        List<GoogleAccountDto> accounts = accountMongo.findGoogleAccountDtos();
        Set<String> seen = new HashSet<>();
        int count = 0;
        for (GoogleAccountDto account : accounts) {
            String advkey = account.getAccountGoogle();
            if (advkey == null || advkey.isBlank() || !seen.add(advkey)) continue;
            producer.sendGoogleAdDaily(account.getUserId());
            count++;
        }
        log.info("[SCHEDULER] 구글 소재 일별 메시지 발행 완료 총={}건", count);
    }

    // 매일 오전 8시 - Google 키워드 일별 수집
    @Scheduled(cron = "0 0 8 * * *", zone = "Asia/Seoul")
    public void scheduleGoogleKeywordDaily() {
        log.info("[SCHEDULER] 구글 키워드 일별 수집 시작");
        List<GoogleAccountDto> accounts = accountMongo.findGoogleAccountDtos();
        Set<String> seen = new HashSet<>();
        int count = 0;
        for (GoogleAccountDto account : accounts) {
            String advkey = account.getAccountGoogle();
            if (advkey == null || advkey.isBlank() || !seen.add(advkey)) continue;
            producer.sendGoogleKeywordDaily(account.getUserId());
            count++;
        }
        log.info("[SCHEDULER] 구글 키워드 일별 메시지 발행 완료 총={}건", count);
    }
}