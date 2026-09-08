package com.ssafy.a107.trading.backtest.engine.performance;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

@Component
public class CostComparisonCalculator {

    private static final int MONEY_SCALE = 8;
    private static final int RATE_SCALE = 8;

    private final CumulativeReturnCalculator cumulativeReturnCalculator;

    public CostComparisonCalculator(
            CumulativeReturnCalculator cumulativeReturnCalculator
    ) {
        this.cumulativeReturnCalculator = cumulativeReturnCalculator;
    }

    public CostComparison calculate(
            BigDecimal initialCapital,
            BigDecimal finalAssetValueAfterCosts,
            TradeStatistics tradeStatistics
    ) {
        requirePositive(
                initialCapital,
                "Initial capital must be greater than zero"
        );
        requireNonNegative(
                finalAssetValueAfterCosts,
                "Final asset value after costs must not be negative"
        );
        Objects.requireNonNull(tradeStatistics);

        BigDecimal finalAssetValueBeforeCosts =
                finalAssetValueAfterCosts
                        .add(tradeStatistics.totalFee())
                        .add(tradeStatistics.totalSlippage())
                        .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal normalizedFinalAssetValueAfterCosts =
                finalAssetValueAfterCosts.setScale(
                        MONEY_SCALE,
                        RoundingMode.HALF_UP
                );

        BigDecimal returnBeforeCosts = cumulativeReturnCalculator.calculate(
                initialCapital,
                finalAssetValueBeforeCosts
        );
        BigDecimal returnAfterCosts = cumulativeReturnCalculator.calculate(
                initialCapital,
                normalizedFinalAssetValueAfterCosts
        );
        BigDecimal costImpactRate = returnBeforeCosts
                .subtract(returnAfterCosts)
                .setScale(RATE_SCALE, RoundingMode.HALF_UP);

        return new CostComparison(
                finalAssetValueBeforeCosts,
                normalizedFinalAssetValueAfterCosts,
                tradeStatistics.totalFee(),
                tradeStatistics.totalSlippage(),
                returnBeforeCosts,
                returnAfterCosts,
                costImpactRate
        );
    }

    private void requirePositive(BigDecimal value, String message) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(message);
        }
    }

    private void requireNonNegative(BigDecimal value, String message) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException(message);
        }
    }
}
