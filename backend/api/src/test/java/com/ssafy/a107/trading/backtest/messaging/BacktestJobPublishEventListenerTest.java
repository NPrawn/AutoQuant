package com.ssafy.a107.trading.backtest.messaging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("백테스트 작업 발행 이벤트 리스너")
class BacktestJobPublishEventListenerTest {

    @Test
    @DisplayName("커밋된 작업 이벤트의 메시지를 발행기에 전달한다")
    void publishesCommittedJobMessage() {
        BacktestJobPublisher publisher = mock(BacktestJobPublisher.class);
        BacktestJobPublishEventListener listener = new BacktestJobPublishEventListener(publisher);
        BacktestJobMessage message = new BacktestJobMessage(
                UUID.randomUUID(),
                UUID.randomUUID(),
                1L,
                "market-v1",
                "engine-v1"
        );

        listener.publish(new BacktestJobQueuedEvent(message));

        verify(publisher).publish(message);
    }
}
