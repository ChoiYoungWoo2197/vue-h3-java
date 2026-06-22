package com.h3.h3_java.batch.scheduler;

import com.h3.h3_java.batch.master.NaverMasterReportJob;
import com.h3.h3_java.queue.producer.CollectorProducer;
import com.h3.h3_java.raw.mongo.AccountMongoService;
import com.h3.h3_java.raw.mongo.NaverMasterMongoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
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

    private final AccountMongoService    accountMongo;
    private final NaverMasterMongoService masterMongoService;
    private final NaverMasterReportJob   masterReportJob;
    private final CollectorProducer      producer;

    private final Set<String> processing = ConcurrentHashMap.newKeySet();
    private final Set<String> initiated  = ConcurrentHashMap.newKeySet();

    @Scheduled(fixedDelay = 600_000, initialDelay = 60_000)
    public void detectAndInit() {
        List<Document> accounts = accountMongo.findNaverAccounts();
        Set<String> seen = new HashSet<>();

        for (Document acc : accounts) {
            String userId = acc.getString("user_id");
            String cid    = acc.getString("account_naver_customer");
            if (shouldSkip(userId)) continue;
            if (cid == null || cid.isBlank() || !seen.add(cid)) continue;
            if (masterMongoService.hasCampaignData(cid)) continue;
            if (initiated.contains(cid)) continue;
            if (!processing.add(cid)) continue;

            try {
                initAccount(userId, cid);
                initiated.add(cid);
            } catch (Exception e) {
                log.error("[NEW-ACCOUNT] 초기 수집 실패 userId={} customerId={} error={}", userId, cid, e.getMessage(), e);
                initiated.add(cid);
            } finally {
                processing.remove(cid);
            }
        }
    }

    private void initAccount(String userId, String cid) {
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
