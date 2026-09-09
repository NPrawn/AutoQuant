package com.ssafy.a107.trading.backtest.messaging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

class BacktestJobDeadLetterPublisherTest {

    @DisplayName("실패한 작업 메시지를 dead-letter exchange로 발행한다")
    @Test
    void publishDeadLetterMessage() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        BacktestJobQueueProperties properties =
                new BacktestJobQueueProperties(
                        "backtest.exchange",
                        "backtest.queue",
                        "backtest.run",
                        "backtest.dead-letter.exchange",
                        "backtest.dead-letter.queue",
                        "backtest.dead-letter"
                );
        BacktestJobMessage job = new BacktestJobMessage(
                UUID.randomUUID(),
                UUID.randomUUID(),
                1L,
                "market-v1",
                "engine-v1"
        );
        IllegalStateException exception = new IllegalStateException("실행 실패");

        new BacktestJobDeadLetterPublisher(rabbitTemplate, properties)
                .publish(job, exception);

        ArgumentCaptor<BacktestJobDeadLetterMessage> messageCaptor =
                ArgumentCaptor.forClass(BacktestJobDeadLetterMessage.class);
        then(rabbitTemplate).should().convertAndSend(
                org.mockito.ArgumentMatchers.eq("backtest.dead-letter.exchange"),
                org.mockito.ArgumentMatchers.eq("backtest.dead-letter"),
                messageCaptor.capture()
        );
        assertThat(messageCaptor.getValue().job()).isEqualTo(job);
        assertThat(messageCaptor.getValue().failureType())
                .isEqualTo("IllegalStateException");
        assertThat(messageCaptor.getValue().failureMessage()).isEqualTo("실행 실패");
    }
}
