package com.h3.h3_java.queue.consumer;

import com.h3.h3_java.batch.master.NaverAdDetailJob;
import com.h3.h3_java.batch.master.NaverGfaMasterJob;
import com.h3.h3_java.batch.stat.NaverGfaCampaignDayCollectionJob;
import com.h3.h3_java.batch.master.NaverMasterReportJob;
import com.h3.h3_java.batch.stat.NaverAdDayCollectionJob;
import com.h3.h3_java.batch.stat.NaverAdGroupDayCollectionJob;
import com.h3.h3_java.batch.stat.NaverCampaignDayCollectionJob;
import com.h3.h3_java.batch.stat.NaverCampaignHourCollectionJob;
import com.h3.h3_java.batch.stat.NaverConvTypeJob;
import com.h3.h3_java.batch.stat.NaverGfaBudgetAlarmJob;
import com.h3.h3_java.batch.stat.NaverShoppingAdDayCollectionJob;
import com.h3.h3_java.batch.stat.NaverStateReportJob;
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
    private final NaverGfaMasterJob naverGfaMasterJob;
    private final NaverGfaCampaignDayCollectionJob naverGfaCampaignDayCollectionJob;
    private final NaverCampaignDayCollectionJob naverCampaignDayCollectionJob;
    private final NaverCampaignHourCollectionJob naverCampaignHourCollectionJob;
    private final NaverAdGroupDayCollectionJob naverAdGroupDayCollectionJob;
    private final NaverStateReportJob naverStateReportJob;
    private final NaverAdDayCollectionJob naverAdDayCollectionJob;
    private final NaverShoppingAdDayCollectionJob naverShoppingAdDayCollectionJob;
    private final NaverConvTypeJob naverConvTypeJob;
    private final NaverGfaBudgetAlarmJob naverGfaBudgetAlarmJob;

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
            if (hasRange(msg)) {
                naverCampaignDayCollectionJob.collectRange(msg.getUserId(), msg.getFromDate(), msg.getToDate());
            } else {
                naverCampaignDayCollectionJob.collectForUserId(msg.getUserId());
            }
        } catch (Exception e) {
            log.error("[MQ][ERROR] NAVER CAMPAIGN DAILY userId={} error={}", msg.getUserId(), e.getMessage(), e);
        }
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_NAVER_CAMPAIGN_HOUR)
    public void consumeNaverCampaignHour(CollectorMessage msg) {
        log.info("[MQ][RECV] NAVER CAMPAIGN HOUR userId={} customerId={}", msg.getUserId(), msg.getCustomerId());
        try {
            if (hasRange(msg)) {
                naverCampaignHourCollectionJob.collectRange(msg.getUserId(), msg.getFromDate(), msg.getToDate());
            } else {
                naverCampaignHourCollectionJob.collectForUserId(msg.getUserId());
            }
        } catch (Exception e) {
            log.error("[MQ][ERROR] NAVER CAMPAIGN HOUR userId={} error={}", msg.getUserId(), e.getMessage(), e);
        }
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_NAVER_ADGROUP_DAILY)
    public void consumeNaverAdGroupDaily(CollectorMessage msg) {
        log.info("[MQ][RECV] NAVER ADGROUP DAILY userId={} customerId={}", msg.getUserId(), msg.getCustomerId());
        try {
            if (hasRange(msg)) {
                naverAdGroupDayCollectionJob.collectRange(msg.getUserId(), msg.getFromDate(), msg.getToDate());
            } else {
                naverAdGroupDayCollectionJob.collectForUserId(msg.getUserId());
            }
        } catch (Exception e) {
            log.error("[MQ][ERROR] NAVER ADGROUP DAILY userId={} error={}", msg.getUserId(), e.getMessage(), e);
        }
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_NAVER_STATE_REPORT)
    public void consumeNaverStateReport(CollectorMessage msg) {
        log.info("[MQ][RECV] NAVER STATE REPORT userId={} customerId={}", msg.getUserId(), msg.getCustomerId());
        try {
            if (hasRange(msg)) {
                naverStateReportJob.collectRange(msg.getUserId(), msg.getFromDate(), msg.getToDate());
            } else {
                naverStateReportJob.collectForUserId(msg.getUserId());
            }
        } catch (Exception e) {
            log.error("[MQ][ERROR] NAVER STATE REPORT userId={} error={}", msg.getUserId(), e.getMessage(), e);
        }
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_NAVER_AD_DAILY)
    public void consumeNaverAdDaily(CollectorMessage msg) {
        log.info("[MQ][RECV] NAVER AD DAILY userId={} customerId={}", msg.getUserId(), msg.getCustomerId());
        try {
            if (hasRange(msg)) {
                naverAdDayCollectionJob.collectRange(msg.getUserId(), msg.getFromDate(), msg.getToDate());
            } else {
                naverAdDayCollectionJob.collectForUserId(msg.getUserId());
            }
        } catch (Exception e) {
            log.error("[MQ][ERROR] NAVER AD DAILY userId={} error={}", msg.getUserId(), e.getMessage(), e);
        }
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_NAVER_SHOPPING_DAILY)
    public void consumeNaverShoppingDaily(CollectorMessage msg) {
        log.info("[MQ][RECV] NAVER SHOPPING DAILY userId={} customerId={}", msg.getUserId(), msg.getCustomerId());
        try {
            if (hasRange(msg)) {
                naverShoppingAdDayCollectionJob.collectRange(msg.getUserId(), msg.getFromDate(), msg.getToDate());
            } else {
                naverShoppingAdDayCollectionJob.collectForUserId(msg.getUserId());
            }
        } catch (Exception e) {
            log.error("[MQ][ERROR] NAVER SHOPPING DAILY userId={} error={}", msg.getUserId(), e.getMessage(), e);
        }
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_NAVER_CONV_TYPE)
    public void consumeNaverConvType(CollectorMessage msg) {
        log.info("[MQ][RECV] NAVER CONV TYPE userId={} customerId={}", msg.getUserId(), msg.getCustomerId());
        try {
            if (hasRange(msg)) {
                naverConvTypeJob.collectRange(msg.getUserId(), msg.getFromDate(), msg.getToDate());
            } else {
                naverConvTypeJob.collectForUserId(msg.getUserId());
            }
        } catch (Exception e) {
            log.error("[MQ][ERROR] NAVER CONV TYPE userId={} error={}", msg.getUserId(), e.getMessage(), e);
        }
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_NAVER_GFA_CAMPAIGN_DAILY)
    public void consumeNaverGfaCampaignDaily(CollectorMessage msg) {
        log.info("[MQ][RECV] NAVER GFA CAMPAIGN DAILY userId={}", msg.getUserId());
        try {
            if (hasRange(msg)) {
                naverGfaCampaignDayCollectionJob.collectRange(msg.getUserId(), msg.getFromDate(), msg.getToDate());
            } else {
                naverGfaCampaignDayCollectionJob.collectForUserId(msg.getUserId());
            }
        } catch (Exception e) {
            log.error("[MQ][ERROR] NAVER GFA CAMPAIGN DAILY userId={} error={}", msg.getUserId(), e.getMessage(), e);
        }
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_NAVER_GFA_MASTER, concurrency = "3")
    public void consumeNaverGfaMaster(CollectorMessage msg) {
        log.info("[MQ][RECV] NAVER GFA MASTER userId={}", msg.getUserId());
        try {
            naverGfaMasterJob.collectForUserId(msg.getUserId());
        } catch (Exception e) {
            log.error("[MQ][ERROR] NAVER GFA MASTER userId={} error={}", msg.getUserId(), e.getMessage(), e);
        }
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_NAVER_GFA_BUDGET_ALARM)
    public void consumeNaverGfaBudgetAlarm(CollectorMessage msg) {
        log.info("[MQ][RECV] NAVER GFA BUDGET ALARM userId={}", msg.getUserId());
        try {
            naverGfaBudgetAlarmJob.collectForUserId(msg.getUserId());
        } catch (Exception e) {
            log.error("[MQ][ERROR] NAVER GFA BUDGET ALARM userId={} error={}", msg.getUserId(), e.getMessage(), e);
        }
    }

    private boolean hasRange(CollectorMessage msg) {
        return msg.getFromDate() != null && !msg.getFromDate().isEmpty();
    }
}
