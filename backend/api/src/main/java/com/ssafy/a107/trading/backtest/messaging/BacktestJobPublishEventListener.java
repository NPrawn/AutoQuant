package com.ssafy.a107.trading.backtest.messaging;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class BacktestJobPublishEventListener {

    private final BacktestJobPublisher publisher;

    public BacktestJobPublishEventListener(BacktestJobPublisher publisher) {
        this.publisher = publisher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(BacktestJobQueuedEvent event) {
        publisher.publish(event.message());
    }
}
