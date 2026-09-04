package com.ssafy.a107.trading.backtest.controller.response;

import com.ssafy.a107.trading.backtest.domain.BacktestRun;
import com.ssafy.a107.trading.backtest.domain.BacktestRunStatus;

import java.time.Instant;
import java.util.UUID;

public record BacktestRunCreatedResponse(
        UUID backtestId,
        BacktestRunStatus status,
        Instant queuedAt
) {

    public static BacktestRunCreatedResponse from(BacktestRun backtestRun) {
        return new BacktestRunCreatedResponse(
                backtestRun.getBacktestId(),
                backtestRun.getStatus(),
                backtestRun.getQueuedAt()
        );
    }
}
