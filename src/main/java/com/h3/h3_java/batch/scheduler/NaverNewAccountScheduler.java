package com.h3.h3_java.batch.scheduler;

import com.h3.h3_java.batch.master.NaverMasterReportJob;
import com.h3.h3_java.media.naver.dto.NaverAccountDto;
import com.h3.h3_java.media.naver.mapper.NaverMasterReportMapper;
import com.h3.h3_java.queue.producer.CollectorProducer;
import com.h3.h3_java.raw.mongo.NaverMasterMongoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class NaverNewAccountScheduler {

    private final NaverMasterReportMapper accountMapper;
    private final NaverMasterMongoService masterMongoService;
    private final NaverMasterReportJob masterReportJob;
    private final CollectorProducer producer;

    private final Set<String> processing = ConcurrentHashMap.newKeySet();
    private final Set<String> initiated  = ConcurrentHashMap.newKeySet();

    @Scheduled(fixedDelay = 600_000, initialDelay = 60_000)
    public void detectAndInit() {
        List<NaverAccountDto> accounts = accountMapper.selectNaverAccounts();
        Set<String> seen = new HashSet<>();

        for (NaverAccountDto acc : accounts) {
            if (shouldSkip(acc.getUserId())) continue;
            String cid = acc.getAccountNaverCustomer();
            if (cid == null || cid.isBlank() || !seen.add(cid)) continue;
            if (masterMongoService.hasCampaignData(cid)) continue;
            if (initiated.contains(cid)) continue;
            if (!processing.add(cid)) continue;

            try {
                initAccount(acc);
                initiated.add(cid);
            } catch (Exception e) {
                log.error("[NEW-ACCOUNT] 초기 수집 실패 userId={} customerId={} error={}", acc.getUserId(), cid, e.getMessage(), e);
                initiated.add(cid);
            } finally {
                processing.remove(cid);
            }
        }
    }

    private void initAccount(NaverAccountDto acc) {
        String userId = acc.getUserId();
        String cid    = acc.getAccountNaverCustomer();

        log.info("[NEW-ACCOUNT] 신규 계정 감지 → 마스터 수집 시작 userId={} customerId={}", userId, cid);
        masterReportJob.collectForUserId(userId, false);
        log.info("[NEW-ACCOUNT] 마스터 수집 완료 → 일별 수집 MQ 발행 userId={} customerId={}", userId, cid);

        producer.sendNaverCampaignDaily(userId, cid);
        producer.sendNaverCampaignHour(userId, cid);
        producer.sendNaverAdGroupDaily(userId, cid);
        producer.sendNaverAdDaily(userId, cid);
        producer.sendNaverShoppingDaily(userId, cid);
        producer.sendNaverStateReport(userId, cid);
        producer.sendNaverConvType(userId, cid);

        log.info("[NEW-ACCOUNT] 초기 수집 완료 userId={} customerId={}", userId, cid);
    }

    private boolean shouldSkip(String userId) {
        return "admin".equals(userId) || "dydrp123".equals(userId);
    }
}
