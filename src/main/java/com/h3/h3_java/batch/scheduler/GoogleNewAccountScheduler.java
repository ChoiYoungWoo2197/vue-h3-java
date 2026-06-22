package com.h3.h3_java.batch.scheduler;

import com.h3.h3_java.batch.master.GoogleMasterJob;
import com.h3.h3_java.queue.producer.CollectorProducer;
import com.h3.h3_java.raw.mongo.AccountMongoService;
import com.h3.h3_java.raw.mongo.GoogleMasterMongoService;
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
public class GoogleNewAccountScheduler {

    private final AccountMongoService    accountMongo;
    private final GoogleMasterMongoService masterMongoService;
    private final GoogleMasterJob        masterJob;
    private final CollectorProducer      producer;

    private final Set<String> processing = ConcurrentHashMap.newKeySet();
    private final Set<String> initiated  = ConcurrentHashMap.newKeySet();

    @Scheduled(fixedDelay = 600_000, initialDelay = 60_000)
    public void detectAndInit() {
        List<Document> accounts = accountMongo.findGoogleAccounts();
        Set<String> seen = new HashSet<>();

        for (Document acc : accounts) {
            String userId = acc.getString("user_id");
            String advkey = acc.getString("account_google");
            if (shouldSkip(userId)) continue;
            if (advkey == null || advkey.isBlank() || !seen.add(advkey)) continue;
            if (masterMongoService.hasCampaignData(advkey)) continue;
            if (initiated.contains(advkey)) continue;
            if (!processing.add(advkey)) continue;

            try {
                initAccount(userId, advkey);
                initiated.add(advkey);
            } catch (Exception e) {
                log.error("[NEW-ACCOUNT][GOOGLE] 초기 수집 실패 userId={} advkey={} error={}", userId, advkey, e.getMessage(), e);
                initiated.add(advkey);
            } finally {
                processing.remove(advkey);
            }
        }
    }

    private void initAccount(String userId, String advkey) {
        log.info("[NEW-ACCOUNT][GOOGLE] 신규 계정 감지 → 마스터 수집 시작 userId={} advkey={}", userId, advkey);
        masterJob.collectForUserId(userId);
        log.info("[NEW-ACCOUNT][GOOGLE] 마스터 수집 완료 → 일별 수집 MQ 발행 userId={} advkey={}", userId, advkey);

        producer.sendGoogleCampaignDaily(userId);
        producer.sendGoogleCampaignHour(userId);
        producer.sendGoogleAdGroupDaily(userId);
        producer.sendGoogleAdDaily(userId);
        producer.sendGoogleKeywordDaily(userId);

        log.info("[NEW-ACCOUNT][GOOGLE] 초기 수집 완료 userId={} advkey={}", userId, advkey);
    }

    private boolean shouldSkip(String userId) {
        return "admin".equals(userId) || "dydrp123".equals(userId);
    }
}
