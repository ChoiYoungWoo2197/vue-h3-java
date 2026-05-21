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

    public void sendNaverMaster(String userId, String customerId, boolean force) {
        CollectorMessage msg = new CollectorMessage("NAVER", "MASTER", userId, customerId, force);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_NAVER_MASTER, msg);
        log.info("[MQ][SEND] NAVER MASTER userId={} customerId={} force={}", userId, customerId, force);
    }

    public void sendNaverCampaignDaily(String userId, String customerId) {
        CollectorMessage msg = new CollectorMessage("NAVER", "CAMPAIGN_DAILY", userId, customerId, false);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_NAVER_CAMPAIGN_DAILY, msg);
        log.info("[MQ][SEND] NAVER CAMPAIGN DAILY userId={} customerId={}", userId, customerId);
    }

    public void sendNaverAdDetail(String userId, String customerId) {
        CollectorMessage msg = new CollectorMessage("NAVER", "AD_DETAIL", userId, customerId, false);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_NAVER_AD_DETAIL, msg);
        log.info("[MQ][SEND] NAVER AD DETAIL userId={} customerId={}", userId, customerId);
    }

    public void sendNaverCampaignHour(String userId, String customerId) {
        CollectorMessage msg = new CollectorMessage("NAVER", "CAMPAIGN_HOUR", userId, customerId, false);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_NAVER_CAMPAIGN_HOUR, msg);
        log.info("[MQ][SEND] NAVER CAMPAIGN HOUR userId={} customerId={}", userId, customerId);
    }

    public void sendNaverAdGroupDaily(String userId, String customerId) {
        CollectorMessage msg = new CollectorMessage("NAVER", "ADGROUP_DAILY", userId, customerId, false);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_NAVER_ADGROUP_DAILY, msg);
        log.info("[MQ][SEND] NAVER ADGROUP DAILY userId={} customerId={}", userId, customerId);
    }

    public void sendNaverStateReport(String userId, String customerId) {
        CollectorMessage msg = new CollectorMessage("NAVER", "STATE_REPORT", userId, customerId, false);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_NAVER_STATE_REPORT, msg);
        log.info("[MQ][SEND] NAVER STATE REPORT userId={} customerId={}", userId, customerId);
    }

    public void sendNaverAdDaily(String userId, String customerId) {
        CollectorMessage msg = new CollectorMessage("NAVER", "AD_DAILY", userId, customerId, false);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_NAVER_AD_DAILY, msg);
        log.info("[MQ][SEND] NAVER AD DAILY userId={} customerId={}", userId, customerId);
    }

    public void sendNaverShoppingDaily(String userId, String customerId) {
        CollectorMessage msg = new CollectorMessage("NAVER", "SHOPPING_DAILY", userId, customerId, false);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_NAVER_SHOPPING_DAILY, msg);
        log.info("[MQ][SEND] NAVER SHOPPING DAILY userId={} customerId={}", userId, customerId);
    }

    public void sendNaverConvType(String userId, String customerId) {
        CollectorMessage msg = new CollectorMessage("NAVER", "CONV_TYPE", userId, customerId, false);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_NAVER_CONV_TYPE, msg);
        log.info("[MQ][SEND] NAVER CONV TYPE userId={} customerId={}", userId, customerId);
    }
}