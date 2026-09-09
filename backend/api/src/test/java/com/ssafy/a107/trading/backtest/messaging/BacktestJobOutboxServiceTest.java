package com.ssafy.a107.trading.backtest.messaging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

@DisplayName("백테스트 작업 Outbox 서비스")
class BacktestJobOutboxServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-09T00:00:00Z");

    @Test
    @DisplayName("미발행 작업을 발행한 뒤 발행 완료로 기록한다")
    void publishPendingJob() {
        BacktestJobOutboxRepository repository = mock(
                BacktestJobOutboxRepository.class
        );
        BacktestJobPublisher publisher = mock(BacktestJobPublisher.class);
        BacktestJobMessage message = message();
        BacktestJobOutbox outbox = new BacktestJobOutbox(message);
        given(repository.findByJobIdForUpdate(message.jobId()))
                .willReturn(Optional.of(outbox));

        boolean published = service(repository, publisher).publish(message.jobId());

        assertThat(published).isTrue();
        assertThat(outbox.isPublished()).isTrue();
        then(publisher).should().publish(message);
    }

    @Test
    @DisplayName("이미 발행한 작업은 다시 발행하지 않는다")
    void skipPublishedJob() {
        BacktestJobOutboxRepository repository = mock(
                BacktestJobOutboxRepository.class
        );
        BacktestJobPublisher publisher = mock(BacktestJobPublisher.class);
        BacktestJobMessage message = message();
        BacktestJobOutbox outbox = new BacktestJobOutbox(message);
        outbox.markPublished(NOW);
        given(repository.findByJobIdForUpdate(message.jobId()))
                .willReturn(Optional.of(outbox));

        boolean published = service(repository, publisher).publish(message.jobId());

        assertThat(published).isFalse();
        then(publisher).shouldHaveNoInteractions();
    }

    private BacktestJobOutboxService service(
            BacktestJobOutboxRepository repository,
            BacktestJobPublisher publisher
    ) {
        return new BacktestJobOutboxService(
                repository,
                publisher,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
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
