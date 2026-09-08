package com.ssafy.a107.trading.backtest.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
@Configuration
@EnableConfigurationProperties(BacktestJobQueueProperties.class)
public class BacktestJobQueueConfig {

    @Bean
    public DirectExchange backtestJobExchange(BacktestJobQueueProperties properties) {
        return ExchangeBuilder
                .directExchange(properties.exchange())
                .durable(true)
                .build();
    }

    @Bean
    public Queue backtestJobQueue(BacktestJobQueueProperties properties) {
        return QueueBuilder
                .durable(properties.queue())
                .build();
    }

    @Bean
    public Binding backtestJobBinding(
            Queue backtestJobQueue,
            DirectExchange backtestJobExchange,
            BacktestJobQueueProperties properties
    ) {
        return BindingBuilder
                .bind(backtestJobQueue)
                .to(backtestJobExchange)
                .with(properties.routingKey());
    }

    @Bean
    public MessageConverter backtestJobMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
