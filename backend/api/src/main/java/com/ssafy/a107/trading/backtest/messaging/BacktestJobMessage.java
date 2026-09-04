package com.ssafy.a107.trading.backtest.messaging;

import com.ssafy.a107.trading.backtest.domain.BacktestRun;

import java.util.Objects;
import java.util.UUID;

public record BacktestJobMessage(
        UUID jobId,
        UUID backtestId,
        Long strategyId,
        String marketDataVersion,
        String engineVersion
) {

    public BacktestJobMessage {
        Objects.requireNonNull(jobId);
        Objects.requireNonNull(backtestId);
        Objects.requireNonNull(strategyId);
        Objects.requireNonNull(marketDataVersion);
        Objects.requireNonNull(engineVersion);
    }

    public static BacktestJobMessage from(UUID jobId, BacktestRun backtestRun) {
        Objects.requireNonNull(backtestRun);
        return new BacktestJobMessage(
                jobId,
                backtestRun.getBacktestId(),
                backtestRun.getStrategy().getStrategyId(),
                backtestRun.getMarketDataVersion(),
                backtestRun.getEngineVersion()
        );
    }
}
