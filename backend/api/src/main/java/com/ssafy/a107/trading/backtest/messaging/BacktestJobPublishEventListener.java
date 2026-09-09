package com.ssafy.a107.trading.backtest.messaging;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class BacktestJobPublishEventListener {

    private final BacktestJobOutboxService backtestJobOutboxService;

    public BacktestJobPublishEventListener(
            BacktestJobOutboxService backtestJobOutboxService
    ) {
        this.backtestJobOutboxService = backtestJobOutboxService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(BacktestJobQueuedEvent event) {
        backtestJobOutboxService.publish(event.message().jobId());
    }
}
