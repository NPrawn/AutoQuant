package com.ssafy.a107.trading.backtest.engine.performance;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record PerformanceReport(
        BigDecimal finalAssetValue,
        BigDecimal cumulativeReturn,
        BigDecimal cagr,
        BigDecimal maximumDrawdown,
        Optional<BigDecimal> sharpeRatio,
        Optional<BigDecimal> volatility,
        Optional<BigDecimal> winRate,
        Optional<BigDecimal> benchmarkReturn,
        Optional<BigDecimal> excessReturn,
        TradeStatistics tradeStatistics,
        CostComparison costComparison,
        List<AssetContribution> assetContributions,
        List<EquitySnapshot> equitySnapshots
) {

    public PerformanceReport {
        finalAssetValue = requireNonNegative(
                finalAssetValue,
                "Final asset value must not be negative"
        );
        cumulativeReturn = Objects.requireNonNull(cumulativeReturn);
        cagr = Objects.requireNonNull(cagr);
        maximumDrawdown = requireRate(maximumDrawdown);
        sharpeRatio = Objects.requireNonNull(sharpeRatio);
        volatility = Objects.requireNonNull(volatility);
        winRate = Objects.requireNonNull(winRate);
        benchmarkReturn = Objects.requireNonNull(benchmarkReturn);
        excessReturn = Objects.requireNonNull(excessReturn);
        tradeStatistics = Objects.requireNonNull(tradeStatistics);
        costComparison = Objects.requireNonNull(costComparison);
        assetContributions = List.copyOf(assetContributions);
        equitySnapshots = List.copyOf(equitySnapshots);

        if (benchmarkReturn.isPresent() != excessReturn.isPresent()) {
            throw new IllegalArgumentException(
                    "Benchmark and excess returns must be present together"
            );
        }
        if (equitySnapshots.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one equity snapshot is required"
            );
        }
        if (equitySnapshots.get(equitySnapshots.size() - 1)
                .totalAssetValue()
                .compareTo(finalAssetValue) != 0) {
            throw new IllegalArgumentException(
                    "Final asset value must match the last equity snapshot"
            );
        }
    }

    private static BigDecimal requireNonNegative(
            BigDecimal value,
            String message
    ) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private static BigDecimal requireRate(BigDecimal value) {
        if (value == null
                || value.signum() < 0
                || value.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException(
                    "Maximum drawdown must be between 0 and 1"
            );
        }
        return value;
    }
}
