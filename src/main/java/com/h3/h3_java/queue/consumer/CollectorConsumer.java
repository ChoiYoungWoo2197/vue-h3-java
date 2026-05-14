package com.h3.h3_java.queue.consumer;

import com.h3.h3_java.batch.master.NaverMasterReportJob;
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

    @RabbitListener(queues = RabbitMQConfig.QUEUE_NAVER_MASTER)
    public void consumeNaverMaster(CollectorMessage msg) {
        log.info("[MQ][RECV] NAVER MASTER userId={} customerId={}", msg.getUserId(), msg.getCustomerId());
        try {
            naverMasterReportJob.collectForUserId(msg.getUserId());
        } catch (Exception e) {
            log.error("[MQ][ERROR] NAVER MASTER userId={} error={}", msg.getUserId(), e.getMessage(), e);
        }
    }
}