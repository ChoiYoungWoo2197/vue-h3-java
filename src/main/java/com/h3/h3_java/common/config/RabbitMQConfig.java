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