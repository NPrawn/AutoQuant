package com.ssafy.a107.trading.backtest.messaging;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class BacktestJobPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final BacktestJobQueueProperties properties;

    public BacktestJobPublisher(
            RabbitTemplate rabbitTemplate,
            BacktestJobQueueProperties properties
    ) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    public void publish(BacktestJobMessage message) {
        rabbitTemplate.convertAndSend(
                properties.exchange(),
                properties.routingKey(),
                message
        );
    }
}
