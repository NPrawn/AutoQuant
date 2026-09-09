package com.ssafy.a107.trading.backtest.messaging;

import java.util.Objects;
import java.util.UUID;

public record BacktestJobDeadLetterMessage(
        BacktestJobMessage job,
        String failureType,
        String failureMessage
) {

    public BacktestJobDeadLetterMessage {
        job = Objects.requireNonNull(job);
        failureType = Objects.requireNonNull(failureType);
        failureMessage = Objects.requireNonNull(failureMessage);
    }

    public static BacktestJobDeadLetterMessage from(
            BacktestJobMessage job,
            Throwable throwable
    ) {
        Objects.requireNonNull(throwable);

        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            message = throwable.getClass().getSimpleName();
        }

        return new BacktestJobDeadLetterMessage(
                job,
                throwable.getClass().getSimpleName(),
                message
        );
    }
}
