package com.ssafy.a107.trading.backtest.engine.performance;

import java.math.BigDecimal;
import java.util.Objects;

public record CostComparison(
        BigDecimal finalAssetValueBeforeCosts,
        BigDecimal finalAssetValueAfterCosts,
        BigDecimal totalFee,
        BigDecimal totalSlippage,
        BigDecimal returnBeforeCosts,
        BigDecimal returnAfterCosts,
        BigDecimal costImpactRate
) {

    public CostComparison {
        finalAssetValueBeforeCosts = requireNonNegative(
                finalAssetValueBeforeCosts,
                "Final asset value before costs must not be negative"
        );
        finalAssetValueAfterCosts = requireNonNegative(
                finalAssetValueAfterCosts,
                "Final asset value after costs must not be negative"
        );
        totalFee = requireNonNegative(
                totalFee,
                "Total fee must not be negative"
        );
        totalSlippage = requireNonNegative(
                totalSlippage,
                "Total slippage must not be negative"
        );
        returnBeforeCosts = Objects.requireNonNull(returnBeforeCosts);
        returnAfterCosts = Objects.requireNonNull(returnAfterCosts);
        costImpactRate = requireNonNegative(
                costImpactRate,
                "Cost impact rate must not be negative"
        );

        if (finalAssetValueBeforeCosts.compareTo(
                finalAssetValueAfterCosts
        ) < 0) {
            throw new IllegalArgumentException(
                    "Final asset value before costs must not be less than after costs"
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
}
