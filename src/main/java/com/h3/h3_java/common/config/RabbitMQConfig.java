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