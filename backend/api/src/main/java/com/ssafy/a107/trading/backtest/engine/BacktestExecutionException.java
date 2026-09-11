package com.ssafy.a107.trading.backtest.engine;

import com.ssafy.a107.trading.backtest.domain.BacktestFailureCode;

import java.util.Objects;

public class BacktestExecutionException extends RuntimeException {

    private final BacktestFailureCode failureCode;

    public BacktestExecutionException(
            BacktestFailureCode failureCode,
            String message
    ) {
        super(message);
        this.failureCode = Objects.requireNonNull(failureCode);
    }

    public BacktestFailureCode getFailureCode() {
        return failureCode;
    }
}
