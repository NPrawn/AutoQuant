package com.ssafy.a107.trading.backtest.engine.performance;

import com.ssafy.a107.trading.backtest.engine.execution.VirtualFill;
import com.ssafy.a107.trading.backtest.engine.portfolio.BacktestPosition;
import com.ssafy.a107.trading.backtest.engine.portfolio.RealizedTradeResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record PerformanceCalculationInput(
        BigDecimal initialCapital,
        List<EquitySnapshot> equitySnapshots,
        List<VirtualFill> fills,
        List<RealizedTradeResult> realizedTrades,
        List<BacktestPosition> openPositions,
        Map<UUID, BigDecimal> finalPrices,
        int periodsPerYear,
        BigDecimal initialBenchmarkPrice,
        BigDecimal finalBenchmarkPrice
) {

    public PerformanceCalculationInput {
        if (initialCapital == null || initialCapital.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Initial capital must be greater than zero"
            );
        }

        equitySnapshots = List.copyOf(
                Objects.requireNonNull(equitySnapshots)
        );
        fills = List.copyOf(Objects.requireNonNull(fills));
        realizedTrades = List.copyOf(Objects.requireNonNull(realizedTrades));
        openPositions = List.copyOf(Objects.requireNonNull(openPositions));
        finalPrices = Map.copyOf(Objects.requireNonNull(finalPrices));

        if (equitySnapshots.size() < 2) {
            throw new IllegalArgumentException(
                    "At least two equity snapshots are required"
            );
        }
        if (periodsPerYear <= 0) {
            throw new IllegalArgumentException(
                    "Periods per year must be greater than zero"
            );
        }
        if ((initialBenchmarkPrice == null)
                != (finalBenchmarkPrice == null)) {
            throw new IllegalArgumentException(
                    "Initial and final benchmark prices must be provided together"
            );
        }
    }
}
