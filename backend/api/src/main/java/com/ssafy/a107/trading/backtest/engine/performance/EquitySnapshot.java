package com.ssafy.a107.trading.backtest.engine.performance;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record EquitySnapshot(
        Instant valuationAt,
        BigDecimal totalAssetValue,
        BigDecimal cashBalance,
        BigDecimal cumulativeReturn,
        BigDecimal drawdownRate
) {

    public EquitySnapshot {
        valuationAt = Objects.requireNonNull(valuationAt);
        totalAssetValue = requireNonNegative(
                totalAssetValue,
                "Total asset value must not be negative"
        );
        cashBalance = requireNonNegative(
                cashBalance,
                "Cash balance must not be negative"
        );
        cumulativeReturn = requireAtLeastNegativeOne(cumulativeReturn);
        drawdownRate = requireRate(drawdownRate);

        if (cashBalance.compareTo(totalAssetValue) > 0) {
            throw new IllegalArgumentException(
                    "Cash balance must not exceed total asset value"
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

    private static BigDecimal requireAtLeastNegativeOne(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ONE.negate()) < 0) {
            throw new IllegalArgumentException(
                    "Cumulative return must not be less than -1"
            );
        }
        return value;
    }

    private static BigDecimal requireRate(BigDecimal value) {
        if (value == null
                || value.signum() < 0
                || value.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException(
                    "Drawdown rate must be between 0 and 1"
            );
        }
        return value;
    }
}
