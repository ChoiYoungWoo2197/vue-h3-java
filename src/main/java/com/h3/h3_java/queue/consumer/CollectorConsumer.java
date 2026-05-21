package com.h3.h3_java.queue.consumer;

import com.h3.h3_java.batch.master.NaverAdDetailJob;
import com.h3.h3_java.batch.master.NaverMasterReportJob;
import com.h3.h3_java.batch.stat.NaverAdGroupDayCollectionJob;
import com.h3.h3_java.batch.stat.NaverCampaignDayCollectionJob;
import com.h3.h3_java.batch.stat.NaverCampaignHourCollectionJob;
import com.h3.h3_java.common.config.RabbitMQConfig;
import com.h3.h3_java.queue.message.CollectorMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CollectorConsumer {

    private final NaverMasterReportJob naverMasterReportJob;
    private final NaverAdDetailJob naverAdDetailJob;
    private final NaverCampaignDayCollectionJob naverCampaignDayCollectionJob;
    private final NaverCampaignHourCollectionJob naverCampaignHourCollectionJob;
    private final NaverAdGroupDayCollectionJob naverAdGroupDayCollectionJob;

    @RabbitListener(queues = RabbitMQConfig.QUEUE_NAVER_MASTER, concurrency = "5")
    public void consumeNaverMaster(CollectorMessage msg) {
        log.info("[MQ][RECV] NAVER MASTER userId={} customerId={}", msg.getUserId(), msg.getCustomerId());
        try {
            naverMasterReportJob.collectForUserId(msg.getUserId(), msg.isForce());
        } catch (Exception e) {
            log.error("[MQ][ERROR] NAVER MASTER userId={} error={}", msg.getUserId(), e.getMessage(), e);
        }
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_NAVER_AD_DETAIL, concurrency = "5")
    public void consumeNaverAdDetail(CollectorMessage msg) {
        log.info("[MQ][RECV] NAVER AD DETAIL userId={} customerId={}", msg.getUserId(), msg.getCustomerId());
        try {
            naverAdDetailJob.collectForUserId(msg.getUserId());
        } catch (Exception e) {
            log.error("[MQ][ERROR] NAVER AD DETAIL userId={} error={}", msg.getUserId(), e.getMessage(), e);
        }
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_NAVER_CAMPAIGN_DAILY)
    public void consumeNaverCampaignDaily(CollectorMessage msg) {
        log.info("[MQ][RECV] NAVER CAMPAIGN DAILY userId={} customerId={}", msg.getUserId(), msg.getCustomerId());
        try {
            naverCampaignDayCollectionJob.collectForUserId(msg.getUserId());
        } catch (Exception e) {
            log.error("[MQ][ERROR] NAVER CAMPAIGN DAILY userId={} error={}", msg.getUserId(), e.getMessage(), e);
        }
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_NAVER_CAMPAIGN_HOUR)
    public void consumeNaverCampaignHour(CollectorMessage msg) {
        log.info("[MQ][RECV] NAVER CAMPAIGN HOUR userId={} customerId={}", msg.getUserId(), msg.getCustomerId());
        try {
            naverCampaignHourCollectionJob.collectForUserId(msg.getUserId());
        } catch (Exception e) {
            log.error("[MQ][ERROR] NAVER CAMPAIGN HOUR userId={} error={}", msg.getUserId(), e.getMessage(), e);
        }
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_NAVER_ADGROUP_DAILY)
    public void consumeNaverAdGroupDaily(CollectorMessage msg) {
        log.info("[MQ][RECV] NAVER ADGROUP DAILY userId={} customerId={}", msg.getUserId(), msg.getCustomerId());
        try {
            naverAdGroupDayCollectionJob.collectForUserId(msg.getUserId());
        } catch (Exception e) {
            log.error("[MQ][ERROR] NAVER ADGROUP DAILY userId={} error={}", msg.getUserId(), e.getMessage(), e);
        }
    }
}