package com.h3.h3_java.common.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "h3.collector";

    public static final String QUEUE_NAVER_MASTER          = "h3.collector.naver.master";
    public static final String ROUTING_NAVER_MASTER        = "naver.master";

    public static final String QUEUE_NAVER_CAMPAIGN_DAILY  = "h3.collector.naver.campaign.daily";
    public static final String ROUTING_NAVER_CAMPAIGN_DAILY = "naver.campaign.daily";

    public static final String QUEUE_NAVER_AD_DETAIL       = "h3.collector.naver.ad.detail";
    public static final String ROUTING_NAVER_AD_DETAIL     = "naver.ad.detail";

    public static final String QUEUE_NAVER_CAMPAIGN_HOUR   = "h3.collector.naver.campaign.hour";
    public static final String ROUTING_NAVER_CAMPAIGN_HOUR = "naver.campaign.hour";

    public static final String QUEUE_NAVER_ADGROUP_DAILY    = "h3.collector.naver.adgroup.daily";
    public static final String ROUTING_NAVER_ADGROUP_DAILY  = "naver.adgroup.daily";

    public static final String QUEUE_NAVER_STATE_REPORT    = "h3.collector.naver.state.report";
    public static final String ROUTING_NAVER_STATE_REPORT  = "naver.state.report";

    public static final String QUEUE_NAVER_AD_DAILY         = "h3.collector.naver.ad.daily";
    public static final String ROUTING_NAVER_AD_DAILY       = "naver.ad.daily";

    public static final String QUEUE_NAVER_SHOPPING_DAILY  = "h3.collector.naver.shopping.daily";
    public static final String ROUTING_NAVER_SHOPPING_DAILY = "naver.shopping.daily";

    public static final String QUEUE_NAVER_CONV_TYPE       = "h3.collector.naver.conv.type";
    public static final String ROUTING_NAVER_CONV_TYPE     = "naver.conv.type";

    public static final String QUEUE_NAVER_GFA_MASTER           = "h3.collector.naver.gfa.master";
    public static final String ROUTING_NAVER_GFA_MASTER         = "naver.gfa.master";

    public static final String QUEUE_NAVER_GFA_CAMPAIGN_DAILY   = "h3.collector.naver.gfa.campaign.daily";
    public static final String ROUTING_NAVER_GFA_CAMPAIGN_DAILY = "naver.gfa.campaign.daily";

    public static final String QUEUE_NAVER_GFA_ADGROUP_DAILY    = "h3.collector.naver.gfa.adgroup.daily";
    public static final String ROUTING_NAVER_GFA_ADGROUP_DAILY  = "naver.gfa.adgroup.daily";

    public static final String QUEUE_NAVER_GFA_AD_DAILY         = "h3.collector.naver.gfa.ad.daily";
    public static final String ROUTING_NAVER_GFA_AD_DAILY       = "naver.gfa.ad.daily";

    public static final String QUEUE_NAVER_GFA_BUDGET_ALARM    = "h3.collector.naver.gfa.budget.alarm";
    public static final String ROUTING_NAVER_GFA_BUDGET_ALARM  = "naver.gfa.budget.alarm";

    public static final String QUEUE_NAVER_GFA_CONV_TYPE   = "h3.collector.naver.gfa.conv.type";
    public static final String ROUTING_NAVER_GFA_CONV_TYPE = "naver.gfa.conv.type";

    // ── Kakao SA ──────────────────────────────────────────────────────────────

    public static final String QUEUE_KAKAO_SA_MASTER             = "h3.collector.kakao.sa.master";
    public static final String ROUTING_KAKAO_SA_MASTER           = "kakao.sa.master";

    public static final String QUEUE_KAKAO_SA_CAMPAIGN_DAILY     = "h3.collector.kakao.sa.campaign.daily";
    public static final String ROUTING_KAKAO_SA_CAMPAIGN_DAILY   = "kakao.sa.campaign.daily";

    public static final String QUEUE_KAKAO_SA_CAMPAIGN_HOUR      = "h3.collector.kakao.sa.campaign.hour";
    public static final String ROUTING_KAKAO_SA_CAMPAIGN_HOUR    = "kakao.sa.campaign.hour";

    public static final String QUEUE_KAKAO_SA_ADGROUP_DAILY      = "h3.collector.kakao.sa.adgroup.daily";
    public static final String ROUTING_KAKAO_SA_ADGROUP_DAILY    = "kakao.sa.adgroup.daily";

    public static final String QUEUE_KAKAO_SA_KEYWORD_DAILY      = "h3.collector.kakao.sa.keyword.daily";
    public static final String ROUTING_KAKAO_SA_KEYWORD_DAILY    = "kakao.sa.keyword.daily";

    public static final String QUEUE_KAKAO_SA_AD_DAILY           = "h3.collector.kakao.sa.ad.daily";
    public static final String ROUTING_KAKAO_SA_AD_DAILY         = "kakao.sa.ad.daily";

    public static final String QUEUE_KAKAO_SA_BUDGET_ALARM       = "h3.collector.kakao.sa.budget.alarm";
    public static final String ROUTING_KAKAO_SA_BUDGET_ALARM     = "kakao.sa.budget.alarm";

    // ── Google ────────────────────────────────────────────────────────────────

    public static final String QUEUE_GOOGLE_MASTER               = "h3.collector.google.master";
    public static final String ROUTING_GOOGLE_MASTER             = "google.master";

    public static final String QUEUE_GOOGLE_CAMPAIGN_DAILY       = "h3.collector.google.campaign.daily";
    public static final String ROUTING_GOOGLE_CAMPAIGN_DAILY     = "google.campaign.daily";

    public static final String QUEUE_GOOGLE_CAMPAIGN_HOUR        = "h3.collector.google.campaign.hour";
    public static final String ROUTING_GOOGLE_CAMPAIGN_HOUR      = "google.campaign.hour";

    public static final String QUEUE_GOOGLE_ADGROUP_DAILY        = "h3.collector.google.adgroup.daily";
    public static final String ROUTING_GOOGLE_ADGROUP_DAILY      = "google.adgroup.daily";

    public static final String QUEUE_GOOGLE_AD_DAILY             = "h3.collector.google.ad.daily";
    public static final String ROUTING_GOOGLE_AD_DAILY           = "google.ad.daily";

    public static final String QUEUE_GOOGLE_KEYWORD_DAILY        = "h3.collector.google.keyword.daily";
    public static final String ROUTING_GOOGLE_KEYWORD_DAILY      = "google.keyword.daily";

    // ── Kakao MO ──────────────────────────────────────────────────────────────

    public static final String QUEUE_KAKAO_MO_MASTER             = "h3.collector.kakao.mo.master";
    public static final String ROUTING_KAKAO_MO_MASTER           = "kakao.mo.master";

    public static final String QUEUE_KAKAO_MO_CAMPAIGN_DAILY     = "h3.collector.kakao.mo.campaign.daily";
    public static final String ROUTING_KAKAO_MO_CAMPAIGN_DAILY   = "kakao.mo.campaign.daily";

    public static final String QUEUE_KAKAO_MO_CAMPAIGN_HOUR      = "h3.collector.kakao.mo.campaign.hour";
    public static final String ROUTING_KAKAO_MO_CAMPAIGN_HOUR    = "kakao.mo.campaign.hour";

    public static final String QUEUE_KAKAO_MO_ADGROUP_DAILY      = "h3.collector.kakao.mo.adgroup.daily";
    public static final String ROUTING_KAKAO_MO_ADGROUP_DAILY    = "kakao.mo.adgroup.daily";

    public static final String QUEUE_KAKAO_MO_AD_DAILY           = "h3.collector.kakao.mo.ad.daily";
    public static final String ROUTING_KAKAO_MO_AD_DAILY         = "kakao.mo.ad.daily";

    public static final String QUEUE_KAKAO_MO_BUDGET_ALARM       = "h3.collector.kakao.mo.budget.alarm";
    public static final String ROUTING_KAKAO_MO_BUDGET_ALARM     = "kakao.mo.budget.alarm";

    @Bean
    public DirectExchange collectorExchange() {
        return new DirectExchange(EXCHANGE);
    }

    @Bean
    public Queue naverMasterQueue() {
        return QueueBuilder.durable(QUEUE_NAVER_MASTER).build();
    }

    @Bean
    public Binding naverMasterBinding(Queue naverMasterQueue, DirectExchange collectorExchange) {
        return BindingBuilder.bind(naverMasterQueue).to(collectorExchange).with(ROUTING_NAVER_MASTER);
    }

    @Bean
    public Queue naverCampaignDailyQueue() {
        return QueueBuilder.durable(QUEUE_NAVER_CAMPAIGN_DAILY).build();
    }

    @Bean
    public Binding naverCampaignDailyBinding(Queue naverCampaignDailyQueue, DirectExchange collectorExchange) {
        return BindingBuilder.bind(naverCampaignDailyQueue).to(collectorExchange).with(ROUTING_NAVER_CAMPAIGN_DAILY);
    }

    @Bean
    public Queue naverAdDetailQueue() {
        return QueueBuilder.durable(QUEUE_NAVER_AD_DETAIL).build();
    }

    @Bean
    public Binding naverAdDetailBinding(Queue naverAdDetailQueue, DirectExchange collectorExchange) {
        return BindingBuilder.bind(naverAdDetailQueue).to(collectorExchange).with(ROUTING_NAVER_AD_DETAIL);
    }

    @Bean
    public Queue naverCampaignHourQueue() {
        return QueueBuilder.durable(QUEUE_NAVER_CAMPAIGN_HOUR).build();
    }

    @Bean
    public Binding naverCampaignHourBinding(Queue naverCampaignHourQueue, DirectExchange collectorExchange) {
        return BindingBuilder.bind(naverCampaignHourQueue).to(collectorExchange).with(ROUTING_NAVER_CAMPAIGN_HOUR);
    }

    @Bean
    public Queue naverAdGroupDailyQueue() {
        return QueueBuilder.durable(QUEUE_NAVER_ADGROUP_DAILY).build();
    }

    @Bean
    public Binding naverAdGroupDailyBinding(Queue naverAdGroupDailyQueue, DirectExchange collectorExchange) {
        return BindingBuilder.bind(naverAdGroupDailyQueue).to(collectorExchange).with(ROUTING_NAVER_ADGROUP_DAILY);
    }

    @Bean
    public Queue naverStateReportQueue() {
        return QueueBuilder.durable(QUEUE_NAVER_STATE_REPORT).build();
    }

    @Bean
    public Binding naverStateReportBinding(Queue naverStateReportQueue, DirectExchange collectorExchange) {
        return BindingBuilder.bind(naverStateReportQueue).to(collectorExchange).with(ROUTING_NAVER_STATE_REPORT);
    }

    @Bean
    public Queue naverAdDailyQueue() {
        return QueueBuilder.durable(QUEUE_NAVER_AD_DAILY).build();
    }

    @Bean
    public Binding naverAdDailyBinding(Queue naverAdDailyQueue, DirectExchange collectorExchange) {
        return BindingBuilder.bind(naverAdDailyQueue).to(collectorExchange).with(ROUTING_NAVER_AD_DAILY);
    }

    @Bean
    public Queue naverShoppingDailyQueue() {
        return QueueBuilder.durable(QUEUE_NAVER_SHOPPING_DAILY).build();
    }

    @Bean
    public Binding naverShoppingDailyBinding(Queue naverShoppingDailyQueue, DirectExchange collectorExchange) {
        return BindingBuilder.bind(naverShoppingDailyQueue).to(collectorExchange).with(ROUTING_NAVER_SHOPPING_DAILY);
    }

    @Bean
    public Queue naverConvTypeQueue() {
        return QueueBuilder.durable(QUEUE_NAVER_CONV_TYPE).build();
    }

    @Bean
    public Binding naverConvTypeBinding(Queue naverConvTypeQueue, DirectExchange collectorExchange) {
        return BindingBuilder.bind(naverConvTypeQueue).to(collectorExchange).with(ROUTING_NAVER_CONV_TYPE);
    }

    @Bean
    public Queue naverGfaMasterQueue() {
        return QueueBuilder.durable(QUEUE_NAVER_GFA_MASTER).build();
    }

    @Bean
    public Binding naverGfaMasterBinding(Queue naverGfaMasterQueue, DirectExchange collectorExchange) {
        return BindingBuilder.bind(naverGfaMasterQueue).to(collectorExchange).with(ROUTING_NAVER_GFA_MASTER);
    }

    @Bean
    public Queue naverGfaCampaignDailyQueue() {
        return QueueBuilder.durable(QUEUE_NAVER_GFA_CAMPAIGN_DAILY).build();
    }

    @Bean
    public Binding naverGfaCampaignDailyBinding(Queue naverGfaCampaignDailyQueue, DirectExchange collectorExchange) {
        return BindingBuilder.bind(naverGfaCampaignDailyQueue).to(collectorExchange).with(ROUTING_NAVER_GFA_CAMPAIGN_DAILY);
    }

    @Bean
    public Queue naverGfaAdDailyQueue() {
        return QueueBuilder.durable(QUEUE_NAVER_GFA_AD_DAILY).build();
    }

    @Bean
    public Binding naverGfaAdDailyBinding(Queue naverGfaAdDailyQueue, DirectExchange collectorExchange) {
        return BindingBuilder.bind(naverGfaAdDailyQueue).to(collectorExchange).with(ROUTING_NAVER_GFA_AD_DAILY);
    }

    @Bean
    public Queue naverGfaAdgroupDailyQueue() {
        return QueueBuilder.durable(QUEUE_NAVER_GFA_ADGROUP_DAILY).build();
    }

    @Bean
    public Binding naverGfaAdgroupDailyBinding(Queue naverGfaAdgroupDailyQueue, DirectExchange collectorExchange) {
        return BindingBuilder.bind(naverGfaAdgroupDailyQueue).to(collectorExchange).with(ROUTING_NAVER_GFA_ADGROUP_DAILY);
    }

    @Bean
    public Queue naverGfaBudgetAlarmQueue() {
        return QueueBuilder.durable(QUEUE_NAVER_GFA_BUDGET_ALARM).build();
    }

    @Bean
    public Binding naverGfaBudgetAlarmBinding(Queue naverGfaBudgetAlarmQueue, DirectExchange collectorExchange) {
        return BindingBuilder.bind(naverGfaBudgetAlarmQueue).to(collectorExchange).with(ROUTING_NAVER_GFA_BUDGET_ALARM);
    }

    @Bean
    public Queue naverGfaConvTypeQueue() {
        return QueueBuilder.durable(QUEUE_NAVER_GFA_CONV_TYPE).build();
    }

    @Bean
    public Binding naverGfaConvTypeBinding(Queue naverGfaConvTypeQueue, DirectExchange collectorExchange) {
        return BindingBuilder.bind(naverGfaConvTypeQueue).to(collectorExchange).with(ROUTING_NAVER_GFA_CONV_TYPE);
    }

    // ── Kakao SA Beans ────────────────────────────────────────────────────────

    @Bean public Queue kakaoSaMasterQueue() { return QueueBuilder.durable(QUEUE_KAKAO_SA_MASTER).build(); }
    @Bean public Binding kakaoSaMasterBinding(Queue kakaoSaMasterQueue, DirectExchange collectorExchange) { return BindingBuilder.bind(kakaoSaMasterQueue).to(collectorExchange).with(ROUTING_KAKAO_SA_MASTER); }

    @Bean public Queue kakaoSaCampaignDailyQueue() { return QueueBuilder.durable(QUEUE_KAKAO_SA_CAMPAIGN_DAILY).build(); }
    @Bean public Binding kakaoSaCampaignDailyBinding(Queue kakaoSaCampaignDailyQueue, DirectExchange collectorExchange) { return BindingBuilder.bind(kakaoSaCampaignDailyQueue).to(collectorExchange).with(ROUTING_KAKAO_SA_CAMPAIGN_DAILY); }

    @Bean public Queue kakaoSaCampaignHourQueue() { return QueueBuilder.durable(QUEUE_KAKAO_SA_CAMPAIGN_HOUR).build(); }
    @Bean public Binding kakaoSaCampaignHourBinding(Queue kakaoSaCampaignHourQueue, DirectExchange collectorExchange) { return BindingBuilder.bind(kakaoSaCampaignHourQueue).to(collectorExchange).with(ROUTING_KAKAO_SA_CAMPAIGN_HOUR); }

    @Bean public Queue kakaoSaAdGroupDailyQueue() { return QueueBuilder.durable(QUEUE_KAKAO_SA_ADGROUP_DAILY).build(); }
    @Bean public Binding kakaoSaAdGroupDailyBinding(Queue kakaoSaAdGroupDailyQueue, DirectExchange collectorExchange) { return BindingBuilder.bind(kakaoSaAdGroupDailyQueue).to(collectorExchange).with(ROUTING_KAKAO_SA_ADGROUP_DAILY); }

    @Bean public Queue kakaoSaKeywordDailyQueue() { return QueueBuilder.durable(QUEUE_KAKAO_SA_KEYWORD_DAILY).build(); }
    @Bean public Binding kakaoSaKeywordDailyBinding(Queue kakaoSaKeywordDailyQueue, DirectExchange collectorExchange) { return BindingBuilder.bind(kakaoSaKeywordDailyQueue).to(collectorExchange).with(ROUTING_KAKAO_SA_KEYWORD_DAILY); }

    @Bean public Queue kakaoSaAdDailyQueue() { return QueueBuilder.durable(QUEUE_KAKAO_SA_AD_DAILY).build(); }
    @Bean public Binding kakaoSaAdDailyBinding(Queue kakaoSaAdDailyQueue, DirectExchange collectorExchange) { return BindingBuilder.bind(kakaoSaAdDailyQueue).to(collectorExchange).with(ROUTING_KAKAO_SA_AD_DAILY); }

    @Bean public Queue kakaoSaBudgetAlarmQueue() { return QueueBuilder.durable(QUEUE_KAKAO_SA_BUDGET_ALARM).build(); }
    @Bean public Binding kakaoSaBudgetAlarmBinding(Queue kakaoSaBudgetAlarmQueue, DirectExchange collectorExchange) { return BindingBuilder.bind(kakaoSaBudgetAlarmQueue).to(collectorExchange).with(ROUTING_KAKAO_SA_BUDGET_ALARM); }

    // ── Kakao MO Beans ────────────────────────────────────────────────────────

    @Bean public Queue kakaoMoMasterQueue() { return QueueBuilder.durable(QUEUE_KAKAO_MO_MASTER).build(); }
    @Bean public Binding kakaoMoMasterBinding(Queue kakaoMoMasterQueue, DirectExchange collectorExchange) { return BindingBuilder.bind(kakaoMoMasterQueue).to(collectorExchange).with(ROUTING_KAKAO_MO_MASTER); }

    @Bean public Queue kakaoMoCampaignDailyQueue() { return QueueBuilder.durable(QUEUE_KAKAO_MO_CAMPAIGN_DAILY).build(); }
    @Bean public Binding kakaoMoCampaignDailyBinding(Queue kakaoMoCampaignDailyQueue, DirectExchange collectorExchange) { return BindingBuilder.bind(kakaoMoCampaignDailyQueue).to(collectorExchange).with(ROUTING_KAKAO_MO_CAMPAIGN_DAILY); }

    @Bean public Queue kakaoMoCampaignHourQueue() { return QueueBuilder.durable(QUEUE_KAKAO_MO_CAMPAIGN_HOUR).build(); }
    @Bean public Binding kakaoMoCampaignHourBinding(Queue kakaoMoCampaignHourQueue, DirectExchange collectorExchange) { return BindingBuilder.bind(kakaoMoCampaignHourQueue).to(collectorExchange).with(ROUTING_KAKAO_MO_CAMPAIGN_HOUR); }

    @Bean public Queue kakaoMoAdGroupDailyQueue() { return QueueBuilder.durable(QUEUE_KAKAO_MO_ADGROUP_DAILY).build(); }
    @Bean public Binding kakaoMoAdGroupDailyBinding(Queue kakaoMoAdGroupDailyQueue, DirectExchange collectorExchange) { return BindingBuilder.bind(kakaoMoAdGroupDailyQueue).to(collectorExchange).with(ROUTING_KAKAO_MO_ADGROUP_DAILY); }

    @Bean public Queue kakaoMoAdDailyQueue() { return QueueBuilder.durable(QUEUE_KAKAO_MO_AD_DAILY).build(); }
    @Bean public Binding kakaoMoAdDailyBinding(Queue kakaoMoAdDailyQueue, DirectExchange collectorExchange) { return BindingBuilder.bind(kakaoMoAdDailyQueue).to(collectorExchange).with(ROUTING_KAKAO_MO_AD_DAILY); }

    @Bean public Queue kakaoMoBudgetAlarmQueue() { return QueueBuilder.durable(QUEUE_KAKAO_MO_BUDGET_ALARM).build(); }
    @Bean public Binding kakaoMoBudgetAlarmBinding(Queue kakaoMoBudgetAlarmQueue, DirectExchange collectorExchange) { return BindingBuilder.bind(kakaoMoBudgetAlarmQueue).to(collectorExchange).with(ROUTING_KAKAO_MO_BUDGET_ALARM); }

    // ── Google Beans ──────────────────────────────────────────────────────────

    @Bean public Queue googleMasterQueue() { return QueueBuilder.durable(QUEUE_GOOGLE_MASTER).build(); }
    @Bean public Binding googleMasterBinding(Queue googleMasterQueue, DirectExchange collectorExchange) { return BindingBuilder.bind(googleMasterQueue).to(collectorExchange).with(ROUTING_GOOGLE_MASTER); }

    @Bean public Queue googleCampaignDailyQueue() { return QueueBuilder.durable(QUEUE_GOOGLE_CAMPAIGN_DAILY).build(); }
    @Bean public Binding googleCampaignDailyBinding(Queue googleCampaignDailyQueue, DirectExchange collectorExchange) { return BindingBuilder.bind(googleCampaignDailyQueue).to(collectorExchange).with(ROUTING_GOOGLE_CAMPAIGN_DAILY); }

    @Bean public Queue googleCampaignHourQueue() { return QueueBuilder.durable(QUEUE_GOOGLE_CAMPAIGN_HOUR).build(); }
    @Bean public Binding googleCampaignHourBinding(Queue googleCampaignHourQueue, DirectExchange collectorExchange) { return BindingBuilder.bind(googleCampaignHourQueue).to(collectorExchange).with(ROUTING_GOOGLE_CAMPAIGN_HOUR); }

    @Bean public Queue googleAdGroupDailyQueue() { return QueueBuilder.durable(QUEUE_GOOGLE_ADGROUP_DAILY).build(); }
    @Bean public Binding googleAdGroupDailyBinding(Queue googleAdGroupDailyQueue, DirectExchange collectorExchange) { return BindingBuilder.bind(googleAdGroupDailyQueue).to(collectorExchange).with(ROUTING_GOOGLE_ADGROUP_DAILY); }

    @Bean public Queue googleAdDailyQueue() { return QueueBuilder.durable(QUEUE_GOOGLE_AD_DAILY).build(); }
    @Bean public Binding googleAdDailyBinding(Queue googleAdDailyQueue, DirectExchange collectorExchange) { return BindingBuilder.bind(googleAdDailyQueue).to(collectorExchange).with(ROUTING_GOOGLE_AD_DAILY); }

    @Bean public Queue googleKeywordDailyQueue() { return QueueBuilder.durable(QUEUE_GOOGLE_KEYWORD_DAILY).build(); }
    @Bean public Binding googleKeywordDailyBinding(Queue googleKeywordDailyQueue, DirectExchange collectorExchange) { return BindingBuilder.bind(googleKeywordDailyQueue).to(collectorExchange).with(ROUTING_GOOGLE_KEYWORD_DAILY); }

    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter());
        return template;
    }
}