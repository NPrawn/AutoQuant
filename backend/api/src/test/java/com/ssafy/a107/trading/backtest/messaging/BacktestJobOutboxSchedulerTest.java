package com.ssafy.a107.trading.backtest.messaging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

@DisplayName("백테스트 작업 Outbox 재발행 스케줄러")
class BacktestJobOutboxSchedulerTest {

    @Test
    @DisplayName("미발행 작업을 모두 Outbox 발행 서비스에 전달한다")
    void publishPendingJobs() {
        BacktestJobOutboxRepository repository = mock(
                BacktestJobOutboxRepository.class
        );
        BacktestJobOutboxService service = mock(BacktestJobOutboxService.class);
        BacktestJobOutbox first = new BacktestJobOutbox(message());
        BacktestJobOutbox second = new BacktestJobOutbox(message());
        given(repository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc())
                .willReturn(List.of(first, second));

        new BacktestJobOutboxScheduler(repository, service).publishPendingJobs();

        then(service).should().publish(first.getJobId());
        then(service).should().publish(second.getJobId());
    }

    private BacktestJobMessage message() {
        return new BacktestJobMessage(
                UUID.randomUUID(),
                UUID.randomUUID(),
                1L,
                "market-v1",
                "engine-v1"
        );
    }
}
