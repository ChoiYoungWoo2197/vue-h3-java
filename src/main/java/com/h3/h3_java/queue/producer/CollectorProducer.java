package com.h3.h3_java.queue.producer;

import com.h3.h3_java.common.config.RabbitMQConfig;
import com.h3.h3_java.queue.message.CollectorMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CollectorProducer {

    private final RabbitTemplate rabbitTemplate;

    public void sendNaverMaster(String userId, String customerId) {
        CollectorMessage msg = new CollectorMessage("NAVER", "MASTER", userId, customerId);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_NAVER_MASTER, msg);
        log.info("[MQ][SEND] NAVER MASTER userId={} customerId={}", userId, customerId);
    }
}