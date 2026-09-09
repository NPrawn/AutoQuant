package com.ssafy.a107.trading.backtest.messaging;

import java.util.Objects;

public record BacktestJobQueuedEvent(
        BacktestJobMessage message
) {

    public BacktestJobQueuedEvent {
        Objects.requireNonNull(message);
    }
}
