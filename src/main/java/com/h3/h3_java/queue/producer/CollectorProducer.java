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

    // =====================================================================
    // MASTER
    // =====================================================================

    public void sendNaverMaster(String userId, String customerId, boolean force) {
        CollectorMessage msg = new CollectorMessage("NAVER", "MASTER", userId, customerId, force);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_NAVER_MASTER, msg);
        log.info("[MQ][SEND] NAVER MASTER userId={} customerId={} force={}", userId, customerId, force);
    }

    // =====================================================================
    // AD DETAIL
    // =====================================================================

    public void sendNaverAdDetail(String userId, String customerId) {
        CollectorMessage msg = new CollectorMessage("NAVER", "AD_DETAIL", userId, customerId, false);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_NAVER_AD_DETAIL, msg);
        log.info("[MQ][SEND] NAVER AD DETAIL userId={} customerId={}", userId, customerId);
    }

    // =====================================================================
    // CAMPAIGN DAILY
    // =====================================================================

    public void sendNaverCampaignDaily(String userId, String customerId) {
        CollectorMessage msg = new CollectorMessage("NAVER", "CAMPAIGN_DAILY", userId, customerId, false);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_NAVER_CAMPAIGN_DAILY, msg);
        log.info("[MQ][SEND] NAVER CAMPAIGN DAILY userId={} customerId={}", userId, customerId);
    }

    public void sendNaverCampaignDailyRange(String userId, String customerId, String from, String to) {
        CollectorMessage msg = new CollectorMessage("NAVER", "CAMPAIGN_DAILY", userId, customerId, false, from, to);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_NAVER_CAMPAIGN_DAILY, msg);
        log.info("[MQ][SEND] NAVER CAMPAIGN DAILY RANGE userId={} from={} to={}", userId, from, to);
    }

    // =====================================================================
    // CAMPAIGN HOUR
    // =====================================================================

    public void sendNaverCampaignHour(String userId, String customerId) {
        CollectorMessage msg = new CollectorMessage("NAVER", "CAMPAIGN_HOUR", userId, customerId, false);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_NAVER_CAMPAIGN_HOUR, msg);
        log.info("[MQ][SEND] NAVER CAMPAIGN HOUR userId={} customerId={}", userId, customerId);
    }

    public void sendNaverCampaignHourRange(String userId, String customerId, String from, String to) {
        CollectorMessage msg = new CollectorMessage("NAVER", "CAMPAIGN_HOUR", userId, customerId, false, from, to);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_NAVER_CAMPAIGN_HOUR, msg);
        log.info("[MQ][SEND] NAVER CAMPAIGN HOUR RANGE userId={} from={} to={}", userId, from, to);
    }

    // =====================================================================
    // ADGROUP DAILY
    // =====================================================================

    public void sendNaverAdGroupDaily(String userId, String customerId) {
        CollectorMessage msg = new CollectorMessage("NAVER", "ADGROUP_DAILY", userId, customerId, false);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_NAVER_ADGROUP_DAILY, msg);
        log.info("[MQ][SEND] NAVER ADGROUP DAILY userId={} customerId={}", userId, customerId);
    }

    public void sendNaverAdGroupDailyRange(String userId, String customerId, String from, String to) {
        CollectorMessage msg = new CollectorMessage("NAVER", "ADGROUP_DAILY", userId, customerId, false, from, to);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_NAVER_ADGROUP_DAILY, msg);
        log.info("[MQ][SEND] NAVER ADGROUP DAILY RANGE userId={} from={} to={}", userId, from, to);
    }

    // =====================================================================
    // AD DAILY
    // =====================================================================

    public void sendNaverAdDaily(String userId, String customerId) {
        CollectorMessage msg = new CollectorMessage("NAVER", "AD_DAILY", userId, customerId, false);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_NAVER_AD_DAILY, msg);
        log.info("[MQ][SEND] NAVER AD DAILY userId={} customerId={}", userId, customerId);
    }

    public void sendNaverAdDailyRange(String userId, String customerId, String from, String to) {
        CollectorMessage msg = new CollectorMessage("NAVER", "AD_DAILY", userId, customerId, false, from, to);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_NAVER_AD_DAILY, msg);
        log.info("[MQ][SEND] NAVER AD DAILY RANGE userId={} from={} to={}", userId, from, to);
    }

    // =====================================================================
    // SHOPPING DAILY
    // =====================================================================

    public void sendNaverShoppingDaily(String userId, String customerId) {
        CollectorMessage msg = new CollectorMessage("NAVER", "SHOPPING_DAILY", userId, customerId, false);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_NAVER_SHOPPING_DAILY, msg);
        log.info("[MQ][SEND] NAVER SHOPPING DAILY userId={} customerId={}", userId, customerId);
    }

    public void sendNaverShoppingDailyRange(String userId, String customerId, String from, String to) {
        CollectorMessage msg = new CollectorMessage("NAVER", "SHOPPING_DAILY", userId, customerId, false, from, to);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_NAVER_SHOPPING_DAILY, msg);
        log.info("[MQ][SEND] NAVER SHOPPING DAILY RANGE userId={} from={} to={}", userId, from, to);
    }

    // =====================================================================
    // STATE REPORT
    // =====================================================================

    public void sendNaverStateReport(String userId, String customerId) {
        CollectorMessage msg = new CollectorMessage("NAVER", "STATE_REPORT", userId, customerId, false);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_NAVER_STATE_REPORT, msg);
        log.info("[MQ][SEND] NAVER STATE REPORT userId={} customerId={}", userId, customerId);
    }

    public void sendNaverStateReportRange(String userId, String customerId, String from, String to) {
        CollectorMessage msg = new CollectorMessage("NAVER", "STATE_REPORT", userId, customerId, false, from, to);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_NAVER_STATE_REPORT, msg);
        log.info("[MQ][SEND] NAVER STATE REPORT RANGE userId={} from={} to={}", userId, from, to);
    }

    // =====================================================================
    // CONV TYPE
    // =====================================================================

    public void sendNaverConvType(String userId, String customerId) {
        CollectorMessage msg = new CollectorMessage("NAVER", "CONV_TYPE", userId, customerId, false);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_NAVER_CONV_TYPE, msg);
        log.info("[MQ][SEND] NAVER CONV TYPE userId={} customerId={}", userId, customerId);
    }

    public void sendNaverConvTypeRange(String userId, String customerId, String from, String to) {
        CollectorMessage msg = new CollectorMessage("NAVER", "CONV_TYPE", userId, customerId, false, from, to);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_NAVER_CONV_TYPE, msg);
        log.info("[MQ][SEND] NAVER CONV TYPE RANGE userId={} from={} to={}", userId, from, to);
    }

    // =====================================================================
    // GFA MASTER
    // =====================================================================

    public void sendNaverGfaMaster(String userId) {
        CollectorMessage msg = new CollectorMessage("NAVER", "GFA_MASTER", userId, null, false);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_NAVER_GFA_MASTER, msg);
        log.info("[MQ][SEND] NAVER GFA MASTER userId={}", userId);
    }

    // =====================================================================
    // GFA CAMPAIGN DAILY
    // =====================================================================

    public void sendNaverGfaCampaignDaily(String userId) {
        CollectorMessage msg = new CollectorMessage("NAVER", "GFA_CAMPAIGN_DAILY", userId, null, false);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_NAVER_GFA_CAMPAIGN_DAILY, msg);
        log.info("[MQ][SEND] NAVER GFA CAMPAIGN DAILY userId={}", userId);
    }

    public void sendNaverGfaCampaignDailyRange(String userId, String from, String to) {
        CollectorMessage msg = new CollectorMessage("NAVER", "GFA_CAMPAIGN_DAILY", userId, null, false, from, to);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_NAVER_GFA_CAMPAIGN_DAILY, msg);
        log.info("[MQ][SEND] NAVER GFA CAMPAIGN DAILY RANGE userId={} from={} to={}", userId, from, to);
    }

    // =====================================================================
    // GFA AD DAILY
    // =====================================================================

    public void sendNaverGfaAdDaily(String userId) {
        CollectorMessage msg = new CollectorMessage("NAVER", "GFA_AD_DAILY", userId, null, false);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_NAVER_GFA_AD_DAILY, msg);
        log.info("[MQ][SEND] NAVER GFA AD DAILY userId={}", userId);
    }

    public void sendNaverGfaAdDailyRange(String userId, String from, String to) {
        CollectorMessage msg = new CollectorMessage("NAVER", "GFA_AD_DAILY", userId, null, false, from, to);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_NAVER_GFA_AD_DAILY, msg);
        log.info("[MQ][SEND] NAVER GFA AD DAILY RANGE userId={} from={} to={}", userId, from, to);
    }

    // =====================================================================
    // GFA ADGROUP DAILY
    // =====================================================================

    public void sendNaverGfaAdgroupDaily(String userId) {
        CollectorMessage msg = new CollectorMessage("NAVER", "GFA_ADGROUP_DAILY", userId, null, false);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_NAVER_GFA_ADGROUP_DAILY, msg);
        log.info("[MQ][SEND] NAVER GFA ADGROUP DAILY userId={}", userId);
    }

    public void sendNaverGfaAdgroupDailyRange(String userId, String from, String to) {
        CollectorMessage msg = new CollectorMessage("NAVER", "GFA_ADGROUP_DAILY", userId, null, false, from, to);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_NAVER_GFA_ADGROUP_DAILY, msg);
        log.info("[MQ][SEND] NAVER GFA ADGROUP DAILY RANGE userId={} from={} to={}", userId, from, to);
    }

    // =====================================================================
    // GFA BUDGET ALARM
    // =====================================================================

    public void sendNaverGfaBudgetAlarm(String userId) {
        CollectorMessage msg = new CollectorMessage("NAVER", "GFA_BUDGET_ALARM", userId, null, false);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_NAVER_GFA_BUDGET_ALARM, msg);
        log.info("[MQ][SEND] NAVER GFA BUDGET ALARM userId={}", userId);
    }

    // =====================================================================
    // GFA CONV TYPE
    // =====================================================================

    public void sendNaverGfaConvType(String userId) {
        CollectorMessage msg = new CollectorMessage("NAVER", "GFA_CONV_TYPE", userId, null, false);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_NAVER_GFA_CONV_TYPE, msg);
        log.info("[MQ][SEND] NAVER GFA CONV TYPE userId={}", userId);
    }

    public void sendNaverGfaConvTypeRange(String userId, String from, String to) {
        CollectorMessage msg = new CollectorMessage("NAVER", "GFA_CONV_TYPE", userId, null, false, from, to);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_NAVER_GFA_CONV_TYPE, msg);
        log.info("[MQ][SEND] NAVER GFA CONV TYPE RANGE userId={} from={} to={}", userId, from, to);
    }

    // =====================================================================
    // KAKAO SA MASTER
    // =====================================================================

    public void sendKakaoSaMaster(String userId) {
        CollectorMessage msg = new CollectorMessage("KAKAO_SA", "MASTER", userId, null, false);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_KAKAO_SA_MASTER, msg);
        log.info("[MQ][SEND] KAKAO SA MASTER userId={}", userId);
    }

    // =====================================================================
    // KAKAO SA CAMPAIGN DAILY
    // =====================================================================

    public void sendKakaoSaCampaignDaily(String userId) {
        CollectorMessage msg = new CollectorMessage("KAKAO_SA", "CAMPAIGN_DAILY", userId, null, false);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_KAKAO_SA_CAMPAIGN_DAILY, msg);
        log.info("[MQ][SEND] KAKAO SA CAMPAIGN DAILY userId={}", userId);
    }

    public void sendKakaoSaCampaignDailyRange(String userId, String from, String to) {
        CollectorMessage msg = new CollectorMessage("KAKAO_SA", "CAMPAIGN_DAILY", userId, null, false, from, to);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_KAKAO_SA_CAMPAIGN_DAILY, msg);
        log.info("[MQ][SEND] KAKAO SA CAMPAIGN DAILY RANGE userId={} from={} to={}", userId, from, to);
    }

    // =====================================================================
    // KAKAO SA CAMPAIGN HOUR
    // =====================================================================

    public void sendKakaoSaCampaignHour(String userId) {
        CollectorMessage msg = new CollectorMessage("KAKAO_SA", "CAMPAIGN_HOUR", userId, null, false);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_KAKAO_SA_CAMPAIGN_HOUR, msg);
        log.info("[MQ][SEND] KAKAO SA CAMPAIGN HOUR userId={}", userId);
    }

    public void sendKakaoSaCampaignHourRange(String userId, String from, String to) {
        CollectorMessage msg = new CollectorMessage("KAKAO_SA", "CAMPAIGN_HOUR", userId, null, false, from, to);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_KAKAO_SA_CAMPAIGN_HOUR, msg);
        log.info("[MQ][SEND] KAKAO SA CAMPAIGN HOUR RANGE userId={} from={} to={}", userId, from, to);
    }

    // =====================================================================
    // KAKAO SA ADGROUP DAILY
    // =====================================================================

    public void sendKakaoSaAdGroupDaily(String userId) {
        CollectorMessage msg = new CollectorMessage("KAKAO_SA", "ADGROUP_DAILY", userId, null, false);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_KAKAO_SA_ADGROUP_DAILY, msg);
        log.info("[MQ][SEND] KAKAO SA ADGROUP DAILY userId={}", userId);
    }

    public void sendKakaoSaAdGroupDailyRange(String userId, String from, String to) {
        CollectorMessage msg = new CollectorMessage("KAKAO_SA", "ADGROUP_DAILY", userId, null, false, from, to);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_KAKAO_SA_ADGROUP_DAILY, msg);
        log.info("[MQ][SEND] KAKAO SA ADGROUP DAILY RANGE userId={} from={} to={}", userId, from, to);
    }

    // =====================================================================
    // KAKAO SA KEYWORD DAILY
    // =====================================================================

    public void sendKakaoSaKeywordDaily(String userId) {
        CollectorMessage msg = new CollectorMessage("KAKAO_SA", "KEYWORD_DAILY", userId, null, false);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_KAKAO_SA_KEYWORD_DAILY, msg);
        log.info("[MQ][SEND] KAKAO SA KEYWORD DAILY userId={}", userId);
    }

    public void sendKakaoSaKeywordDailyRange(String userId, String from, String to) {
        CollectorMessage msg = new CollectorMessage("KAKAO_SA", "KEYWORD_DAILY", userId, null, false, from, to);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_KAKAO_SA_KEYWORD_DAILY, msg);
        log.info("[MQ][SEND] KAKAO SA KEYWORD DAILY RANGE userId={} from={} to={}", userId, from, to);
    }

    // =====================================================================
    // KAKAO SA AD DAILY
    // =====================================================================

    public void sendKakaoSaAdDaily(String userId) {
        CollectorMessage msg = new CollectorMessage("KAKAO_SA", "AD_DAILY", userId, null, false);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_KAKAO_SA_AD_DAILY, msg);
        log.info("[MQ][SEND] KAKAO SA AD DAILY userId={}", userId);
    }

    public void sendKakaoSaAdDailyRange(String userId, String from, String to) {
        CollectorMessage msg = new CollectorMessage("KAKAO_SA", "AD_DAILY", userId, null, false, from, to);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_KAKAO_SA_AD_DAILY, msg);
        log.info("[MQ][SEND] KAKAO SA AD DAILY RANGE userId={} from={} to={}", userId, from, to);
    }

    // =====================================================================
    // KAKAO SA BUDGET ALARM
    // =====================================================================

    public void sendKakaoSaBudgetAlarm(String userId) {
        CollectorMessage msg = new CollectorMessage("KAKAO_SA", "BUDGET_ALARM", userId, null, false);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_KAKAO_SA_BUDGET_ALARM, msg);
        log.info("[MQ][SEND] KAKAO SA BUDGET ALARM userId={}", userId);
    }

    // =====================================================================
    // KAKAO MO MASTER
    // =====================================================================

    public void sendKakaoMoMaster(String userId) {
        CollectorMessage msg = new CollectorMessage("KAKAO_MO", "MASTER", userId, null, false);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_KAKAO_MO_MASTER, msg);
        log.info("[MQ][SEND] KAKAO MO MASTER userId={}", userId);
    }

    // =====================================================================
    // KAKAO MO CAMPAIGN DAILY
    // =====================================================================

    public void sendKakaoMoCampaignDaily(String userId) {
        CollectorMessage msg = new CollectorMessage("KAKAO_MO", "CAMPAIGN_DAILY", userId, null, false);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_KAKAO_MO_CAMPAIGN_DAILY, msg);
        log.info("[MQ][SEND] KAKAO MO CAMPAIGN DAILY userId={}", userId);
    }

    public void sendKakaoMoCampaignDailyRange(String userId, String from, String to) {
        CollectorMessage msg = new CollectorMessage("KAKAO_MO", "CAMPAIGN_DAILY", userId, null, false, from, to);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_KAKAO_MO_CAMPAIGN_DAILY, msg);
        log.info("[MQ][SEND] KAKAO MO CAMPAIGN DAILY RANGE userId={} from={} to={}", userId, from, to);
    }

    // =====================================================================
    // KAKAO MO CAMPAIGN HOUR
    // =====================================================================

    public void sendKakaoMoCampaignHour(String userId) {
        CollectorMessage msg = new CollectorMessage("KAKAO_MO", "CAMPAIGN_HOUR", userId, null, false);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_KAKAO_MO_CAMPAIGN_HOUR, msg);
        log.info("[MQ][SEND] KAKAO MO CAMPAIGN HOUR userId={}", userId);
    }

    public void sendKakaoMoCampaignHourRange(String userId, String from, String to) {
        CollectorMessage msg = new CollectorMessage("KAKAO_MO", "CAMPAIGN_HOUR", userId, null, false, from, to);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_KAKAO_MO_CAMPAIGN_HOUR, msg);
        log.info("[MQ][SEND] KAKAO MO CAMPAIGN HOUR RANGE userId={} from={} to={}", userId, from, to);
    }

    // =====================================================================
    // KAKAO MO ADGROUP DAILY
    // =====================================================================

    public void sendKakaoMoAdGroupDaily(String userId) {
        CollectorMessage msg = new CollectorMessage("KAKAO_MO", "ADGROUP_DAILY", userId, null, false);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_KAKAO_MO_ADGROUP_DAILY, msg);
        log.info("[MQ][SEND] KAKAO MO ADGROUP DAILY userId={}", userId);
    }

    public void sendKakaoMoAdGroupDailyRange(String userId, String from, String to) {
        CollectorMessage msg = new CollectorMessage("KAKAO_MO", "ADGROUP_DAILY", userId, null, false, from, to);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_KAKAO_MO_ADGROUP_DAILY, msg);
        log.info("[MQ][SEND] KAKAO MO ADGROUP DAILY RANGE userId={} from={} to={}", userId, from, to);
    }

    // =====================================================================
    // KAKAO MO AD DAILY
    // =====================================================================

    public void sendKakaoMoAdDaily(String userId) {
        CollectorMessage msg = new CollectorMessage("KAKAO_MO", "AD_DAILY", userId, null, false);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_KAKAO_MO_AD_DAILY, msg);
        log.info("[MQ][SEND] KAKAO MO AD DAILY userId={}", userId);
    }

    public void sendKakaoMoAdDailyRange(String userId, String from, String to) {
        CollectorMessage msg = new CollectorMessage("KAKAO_MO", "AD_DAILY", userId, null, false, from, to);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_KAKAO_MO_AD_DAILY, msg);
        log.info("[MQ][SEND] KAKAO MO AD DAILY RANGE userId={} from={} to={}", userId, from, to);
    }

    // =====================================================================
    // KAKAO MO BUDGET ALARM
    // =====================================================================

    public void sendKakaoMoBudgetAlarm(String userId) {
        CollectorMessage msg = new CollectorMessage("KAKAO_MO", "BUDGET_ALARM", userId, null, false);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_KAKAO_MO_BUDGET_ALARM, msg);
        log.info("[MQ][SEND] KAKAO MO BUDGET ALARM userId={}", userId);
    }
}
