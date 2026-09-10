package com.ssafy.a107.trading.simulation.controller.response;

import com.ssafy.a107.strategy.domain.dsl.RebalancePeriod;
import com.ssafy.a107.strategy.domain.dsl.Timeframe;
import com.ssafy.a107.trading.backtest.domain.ExecutionPriceRule;
import com.ssafy.a107.trading.simulation.domain.SimulationRun;
import com.ssafy.a107.trading.simulation.domain.SimulationRunStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SimulationRunResponse(
        UUID simulationId, Long strategyId, SimulationRunStatus status, Timeframe timeframe,
        String quoteCurrency, BigDecimal initialCapital, BigDecimal cashBalance, BigDecimal feeRate,
        BigDecimal slippageBps, RebalancePeriod rebalanceInterval, ExecutionPriceRule executionPriceRule,
        String engineVersion, Instant startedAt, Instant stoppedAt, Instant lastCandleAt,
        Instant lastRebalanceAt, String failureReason
) {
    public static SimulationRunResponse from(SimulationRun simulationRun) {
        return new SimulationRunResponse(
                simulationRun.getSimulationId(), simulationRun.getStrategy().getStrategyId(), simulationRun.getStatus(),
                simulationRun.getTimeframe(), simulationRun.getQuoteCurrency(), simulationRun.getInitialCapital(),
                simulationRun.getCashBalance(), simulationRun.getFeeRate(), simulationRun.getSlippageBps(),
                simulationRun.getRebalanceInterval(), simulationRun.getExecutionPriceRule(), simulationRun.getEngineVersion(),
                simulationRun.getStartedAt(), simulationRun.getStoppedAt(), simulationRun.getLastCandleAt(),
                simulationRun.getLastRebalanceAt(), simulationRun.getFailureReason()
        );
    }
}
