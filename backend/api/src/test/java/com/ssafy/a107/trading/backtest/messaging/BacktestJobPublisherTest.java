package com.ssafy.a107.trading.backtest.messaging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("백테스트 작업 메시지 발행기")
class BacktestJobPublisherTest {

    private static final BacktestJobQueueProperties PROPERTIES = new BacktestJobQueueProperties(
            "backtest.exchange",
            "backtest.queue",
            "backtest.run",
            "backtest.dead-letter.exchange",
            "backtest.dead-letter.queue",
            "backtest.dead-letter"
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
        acknowledgePublish(rabbitTemplate, true);

        publisher.publish(message);

        verify(rabbitTemplate).convertAndSend(
                eq(PROPERTIES.exchange()),
                eq(PROPERTIES.routingKey()),
                eq(message),
                any(CorrelationData.class)
        );
    }

    @Test
    @DisplayName("RabbitMQ가 발행을 거절하면 예외를 발생시킨다")
    void rejectUnacknowledgedJobMessage() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        BacktestJobPublisher publisher = new BacktestJobPublisher(
                rabbitTemplate,
                PROPERTIES
        );
        BacktestJobMessage message = new BacktestJobMessage(
                UUID.randomUUID(),
                UUID.randomUUID(),
                1L,
                "market-v1",
                "engine-v1"
        );
        acknowledgePublish(rabbitTemplate, false);

        assertThatIllegalStateException()
                .isThrownBy(() -> publisher.publish(message))
                .withMessage("Backtest job publish was not acknowledged by RabbitMQ");
    }

    private void acknowledgePublish(
            RabbitTemplate rabbitTemplate,
            boolean acknowledged
    ) {
        doAnswer(invocation -> {
            CorrelationData correlationData = invocation.getArgument(3);
            correlationData.getFuture().complete(
                    new CorrelationData.Confirm(acknowledged, null)
            );
            return null;
        }).when(rabbitTemplate).convertAndSend(
                eq(PROPERTIES.exchange()),
                eq(PROPERTIES.routingKey()),
                any(BacktestJobMessage.class),
                any(CorrelationData.class)
        );
    }
}
