package com.ssafy.a107.trading.backtest.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
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
                .withArgument(
                        "x-dead-letter-exchange",
                        properties.deadLetterExchange()
                )
                .withArgument(
                        "x-dead-letter-routing-key",
                        properties.deadLetterRoutingKey()
                )
                .build();
    }

    @Bean
    public Binding backtestJobBinding(
            @Qualifier("backtestJobQueue") Queue backtestJobQueue,
            @Qualifier("backtestJobExchange") DirectExchange backtestJobExchange,
            BacktestJobQueueProperties properties
    ) {
        return BindingBuilder
                .bind(backtestJobQueue)
                .to(backtestJobExchange)
                .with(properties.routingKey());
    }

    @Bean
    public DirectExchange backtestJobDeadLetterExchange(
            BacktestJobQueueProperties properties
    ) {
        return ExchangeBuilder.directExchange(properties.deadLetterExchange())
                .durable(true)
                .build();
    }

    @Bean
    public Queue backtestJobDeadLetterQueue(
            BacktestJobQueueProperties properties
    ) {
        return QueueBuilder.durable(properties.deadLetterQueue()).build();
    }

    @Bean
    public Binding backtestJobDeadLetterBinding(
            @Qualifier("backtestJobDeadLetterQueue") Queue backtestJobDeadLetterQueue,
            @Qualifier("backtestJobDeadLetterExchange") DirectExchange backtestJobDeadLetterExchange,
            BacktestJobQueueProperties properties
    ) {
        return BindingBuilder.bind(backtestJobDeadLetterQueue)
                .to(backtestJobDeadLetterExchange)
                .with(properties.deadLetterRoutingKey());
    }

    @Bean
    public MessageConverter backtestJobMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
