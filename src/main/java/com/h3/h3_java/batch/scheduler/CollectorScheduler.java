package com.h3.h3_java.batch.scheduler;

import com.h3.h3_java.media.naver.dto.NaverAccountDto;
import com.h3.h3_java.media.naver.mapper.NaverMasterReportMapper;
import com.h3.h3_java.queue.producer.CollectorProducer;
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

    private final NaverMasterReportMapper mapper;
    private final CollectorProducer producer;

    // 매일 새벽 2시 - 마스터 수집
    @Scheduled(cron = "0 0 2 * * *", zone = "Asia/Seoul")
    public void scheduleNaverMaster() {
        log.info("[SCHEDULER] 네이버 마스터 수집 시작");
        List<NaverAccountDto> accounts = mapper.selectNaverAccounts();
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
        List<NaverAccountDto> accounts = mapper.selectNaverAccounts();
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

    // 매일 새벽 5시 - 키워드·타겟 일별 수집 (StateReport)
    @Scheduled(cron = "0 0 5 * * *", zone = "Asia/Seoul")
    public void scheduleNaverStateReport() {
        log.info("[SCHEDULER] 네이버 StateReport 수집 시작");
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
        log.info("[SCHEDULER] 네이버 StateReport 메시지 발행 완료 총={}건", count);
    }

    // 매일 새벽 4시 - 광고그룹 일별 수집
    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    public void scheduleNaverAdGroupDaily() {
        log.info("[SCHEDULER] 네이버 광고그룹 일별 수집 시작");
        List<NaverAccountDto> accounts = mapper.selectNaverAccounts();
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
        List<NaverAccountDto> accounts = mapper.selectNaverAccounts();
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
}