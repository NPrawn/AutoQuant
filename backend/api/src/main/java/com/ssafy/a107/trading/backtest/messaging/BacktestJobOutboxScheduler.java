package com.ssafy.a107.trading.backtest.messaging;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@Profile("!backtest-worker")
public class BacktestJobOutboxScheduler {

    private final BacktestJobOutboxRepository backtestJobOutboxRepository;
    private final BacktestJobOutboxService backtestJobOutboxService;

    public BacktestJobOutboxScheduler(
            BacktestJobOutboxRepository backtestJobOutboxRepository,
            BacktestJobOutboxService backtestJobOutboxService
    ) {
        this.backtestJobOutboxRepository = Objects.requireNonNull(
                backtestJobOutboxRepository
        );
        this.backtestJobOutboxService = Objects.requireNonNull(
                backtestJobOutboxService
        );
    }

    @Scheduled(fixedDelayString = "${backtest.job.outbox-poll-interval:PT30S}")
    public void publishPendingJobs() {
        backtestJobOutboxRepository
                .findTop100ByPublishedAtIsNullOrderByCreatedAtAsc()
                .forEach(outbox -> backtestJobOutboxService.publish(
                        outbox.getJobId()
                ));
    }
}
