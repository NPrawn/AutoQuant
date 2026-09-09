package com.ssafy.a107.trading.backtest.messaging;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class BacktestJobDeadLetterPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final BacktestJobQueueProperties properties;

    public BacktestJobDeadLetterPublisher(
            RabbitTemplate rabbitTemplate,
            BacktestJobQueueProperties properties
    ) {
        this.rabbitTemplate = Objects.requireNonNull(rabbitTemplate);
        this.properties = Objects.requireNonNull(properties);
    }

    public void publish(BacktestJobMessage job, Throwable throwable) {
        rabbitTemplate.convertAndSend(
                properties.deadLetterExchange(),
                properties.deadLetterRoutingKey(),
                BacktestJobDeadLetterMessage.from(job, throwable)
        );
    }
}
