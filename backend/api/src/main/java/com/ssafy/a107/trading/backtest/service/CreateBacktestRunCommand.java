package com.ssafy.a107.trading.backtest.service;

import com.ssafy.a107.trading.backtest.domain.ExecutionPriceRule;

import java.math.BigDecimal;
import java.time.Instant;

public record CreateBacktestRunCommand(
        Long strategyId,
        Instant periodStart,
        Instant periodEnd,
        String quoteCurrency,
        BigDecimal initialCapital,
        BigDecimal feeRate,
        BigDecimal slippageBps,
        ExecutionPriceRule executionPriceRule,
        String engineVersion,
        String marketDataVersion
) {
}
