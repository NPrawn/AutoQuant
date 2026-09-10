package com.ssafy.a107.trading.simulation.service;

import com.ssafy.a107.trading.backtest.domain.ExecutionPriceRule;

import java.math.BigDecimal;

public record CreateSimulationRunCommand(
        Long strategyId,
        BigDecimal initialCapital,
        BigDecimal feeRate,
        BigDecimal slippageBps,
        ExecutionPriceRule executionPriceRule,
        String engineVersion
) {
}
