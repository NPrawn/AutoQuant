package com.ssafy.a107.trading.backtest.messaging;

import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
public class BacktestJobOutboxService {

    private final BacktestJobOutboxRepository backtestJobOutboxRepository;
    private final BacktestJobPublisher backtestJobPublisher;
    private final Clock clock;

    public BacktestJobOutboxService(
            BacktestJobOutboxRepository backtestJobOutboxRepository,
            BacktestJobPublisher backtestJobPublisher,
            Clock clock
    ) {
        this.backtestJobOutboxRepository = Objects.requireNonNull(
                backtestJobOutboxRepository
        );
        this.backtestJobPublisher = Objects.requireNonNull(backtestJobPublisher);
        this.clock = Objects.requireNonNull(clock);
    }

    public void enqueue(BacktestJobMessage message) {
        backtestJobOutboxRepository.save(new BacktestJobOutbox(message));
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public boolean publish(UUID jobId) {
        Objects.requireNonNull(jobId);

        BacktestJobOutbox outbox = backtestJobOutboxRepository
                .findByJobIdForUpdate(jobId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Backtest job outbox not found"
                ));

        if (outbox.isPublished()) {
            return false;
        }

        backtestJobPublisher.publish(outbox.toMessage());
        outbox.markPublished(Instant.now(clock));
        return true;
    }
}
