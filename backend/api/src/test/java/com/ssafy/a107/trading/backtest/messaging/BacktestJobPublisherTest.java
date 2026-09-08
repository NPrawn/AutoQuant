package com.ssafy.a107.trading.backtest.messaging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("백테스트 작업 메시지 발행기")
class BacktestJobPublisherTest {

    private static final BacktestJobQueueProperties PROPERTIES = new BacktestJobQueueProperties(
            "backtest.exchange",
            "backtest.queue",
            "backtest.run"
    );

    @Test
    @DisplayName("설정된 Exchange와 routing key로 작업 메시지를 발행한다")
    void publishesJobMessage() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        BacktestJobPublisher publisher = new BacktestJobPublisher(rabbitTemplate, PROPERTIES);
        BacktestJobMessage message = new BacktestJobMessage(
                UUID.randomUUID(),
                UUID.randomUUID(),
                1L,
                "market-v1",
                "engine-v1"
        );

        publisher.publish(message);

        verify(rabbitTemplate).convertAndSend(
                PROPERTIES.exchange(),
                PROPERTIES.routingKey(),
                message
        );
    }
}
