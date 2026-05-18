package com.h3.h3_java.batch.scheduler;

import com.h3.h3_java.media.naver.dto.NaverAccountDto;
import com.h3.h3_java.media.naver.mapper.NaverMasterReportMapper;
import com.h3.h3_java.queue.producer.CollectorProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

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
        for (NaverAccountDto account : accounts) {
            if ("admin".equals(account.getUserId())) continue;
            producer.sendNaverMaster(account.getUserId(), account.getAccountNaverCustomer());
        }
        log.info("[SCHEDULER] 네이버 마스터 메시지 발행 완료 총={}건", accounts.size());
    }

    // 매일 새벽 3시 - 캠페인 일별 수집 (마스터 완료 후)
    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Seoul")
    public void scheduleNaverCampaignDaily() {
        log.info("[SCHEDULER] 네이버 캠페인 일별 수집 시작");
        List<NaverAccountDto> accounts = mapper.selectNaverAccounts();
        for (NaverAccountDto account : accounts) {
            if ("admin".equals(account.getUserId())) continue;
            producer.sendNaverCampaignDaily(account.getUserId(), account.getAccountNaverCustomer());
        }
        log.info("[SCHEDULER] 네이버 캠페인 일별 메시지 발행 완료 총={}건", accounts.size());
    }
}