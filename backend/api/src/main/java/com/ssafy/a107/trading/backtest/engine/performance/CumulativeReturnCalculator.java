package com.ssafy.a107.trading.backtest.engine.performance;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

@Component
public class CumulativeReturnCalculator {

    private static final int RATE_SCALE = 8;

    public BigDecimal calculate(
            BigDecimal initialAssetValue,
            BigDecimal currentAssetValue
    ) {
        requirePositive(
                initialAssetValue,
                "Initial asset value must be greater than zero"
        );
        requireNonNegative(
                currentAssetValue,
                "Current asset value must not be negative"
        );

        return currentAssetValue.divide(
                        initialAssetValue,
                        MathContext.DECIMAL128
                )
                .subtract(BigDecimal.ONE)
                .setScale(RATE_SCALE, RoundingMode.HALF_UP);
    }

    private void requirePositive(
            BigDecimal value,
            String message
    ) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(message);
        }
    }

    private void requireNonNegative(
            BigDecimal value,
            String message
    ) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException(message);
        }
    }
}
