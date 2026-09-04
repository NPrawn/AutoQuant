package com.ssafy.a107.trading.backtest.controller.response;

import com.ssafy.a107.strategy.domain.dsl.RebalancePeriod;
import com.ssafy.a107.strategy.domain.dsl.Timeframe;
import com.ssafy.a107.trading.backtest.domain.BacktestRun;
import com.ssafy.a107.trading.backtest.domain.BacktestRunStatus;
import com.ssafy.a107.trading.backtest.domain.ExecutionPriceRule;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BacktestRunResponse(
        UUID backtestId,
        Long strategyId,
        BacktestRunStatus status,
        Instant periodStart,
        Instant periodEnd,
        Timeframe timeframe,
        String quoteCurrency,
        BigDecimal initialCapital,
        BigDecimal feeRate,
        BigDecimal slippageBps,
        RebalancePeriod rebalanceInterval,
        ExecutionPriceRule executionPriceRule,
        String engineVersion,
        String marketDataVersion,
        Instant queuedAt,
        Instant startedAt,
        Instant completedAt
) {

    public static BacktestRunResponse from(BacktestRun backtestRun) {
        return new BacktestRunResponse(
                backtestRun.getBacktestId(),
                backtestRun.getStrategy().getStrategyId(),
                backtestRun.getStatus(),
                backtestRun.getPeriodStart(),
                backtestRun.getPeriodEnd(),
                backtestRun.getTimeframe(),
                backtestRun.getQuoteCurrency(),
                backtestRun.getInitialCapital(),
                backtestRun.getFeeRate(),
                backtestRun.getSlippageBps(),
                backtestRun.getRebalanceInterval(),
                backtestRun.getExecutionPriceRule(),
                backtestRun.getEngineVersion(),
                backtestRun.getMarketDataVersion(),
                backtestRun.getQueuedAt(),
                backtestRun.getStartedAt(),
                backtestRun.getCompletedAt()
        );
    }
}
