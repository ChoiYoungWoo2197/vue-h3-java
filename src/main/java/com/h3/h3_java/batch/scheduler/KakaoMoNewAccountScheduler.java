package com.h3.h3_java.batch.scheduler;

import com.h3.h3_java.batch.master.KakaoMoMasterJob;
import com.h3.h3_java.media.kakao.dto.KakaoMoAccountDto;
import com.h3.h3_java.media.kakao.mapper.KakaoMoMapper;
import com.h3.h3_java.queue.producer.CollectorProducer;
import com.h3.h3_java.raw.mongo.KakaoMoMasterMongoService;
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
public class KakaoMoNewAccountScheduler {

    private final KakaoMoMapper accountMapper;
    private final KakaoMoMasterMongoService masterMongoService;
    private final KakaoMoMasterJob masterJob;
    private final CollectorProducer producer;

    private final Set<String> processing = ConcurrentHashMap.newKeySet();
    private final Set<String> initiated  = ConcurrentHashMap.newKeySet();

    @Scheduled(fixedDelay = 600_000, initialDelay = 60_000)
    public void detectAndInit() {
        List<KakaoMoAccountDto> accounts = accountMapper.selectKakaoMoAccounts();
        Set<String> seen = new HashSet<>();

        for (KakaoMoAccountDto acc : accounts) {
            if (shouldSkip(acc.getUserId())) continue;
            String advkey = acc.getAccountKakaomoment();
            if (advkey == null || advkey.isBlank() || !seen.add(advkey)) continue;
            if (masterMongoService.hasCampaignData(advkey)) continue;
            if (initiated.contains(advkey)) continue;
            if (!processing.add(advkey)) continue;

            try {
                initAccount(acc);
                initiated.add(advkey);
            } catch (Exception e) {
                log.error("[NEW-ACCOUNT][KAKAO-MO] 초기 수집 실패 userId={} advkey={} error={}", acc.getUserId(), advkey, e.getMessage(), e);
                initiated.add(advkey);
            } finally {
                processing.remove(advkey);
            }
        }
    }

    private void initAccount(KakaoMoAccountDto acc) {
        String userId = acc.getUserId();
        String advkey = acc.getAccountKakaomoment();

        log.info("[NEW-ACCOUNT][KAKAO-MO] 신규 계정 감지 → 마스터 수집 시작 userId={} advkey={}", userId, advkey);
        masterJob.collectForUserId(userId);
        log.info("[NEW-ACCOUNT][KAKAO-MO] 마스터 수집 완료 → 일별 수집 MQ 발행 userId={} advkey={}", userId, advkey);

        producer.sendKakaoMoCampaignDaily(userId);
        producer.sendKakaoMoCampaignHour(userId);
        producer.sendKakaoMoAdGroupDaily(userId);
        producer.sendKakaoMoAdDaily(userId);
        producer.sendKakaoMoBudgetAlarm(userId);

        log.info("[NEW-ACCOUNT][KAKAO-MO] 초기 수집 완료 userId={} advkey={}", userId, advkey);
    }

    private boolean shouldSkip(String userId) {
        return "admin".equals(userId) || "dydrp123".equals(userId);
    }
}
