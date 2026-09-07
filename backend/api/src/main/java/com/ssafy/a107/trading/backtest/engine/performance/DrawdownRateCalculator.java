package com.ssafy.a107.trading.backtest.engine.performance;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

@Component
public class DrawdownRateCalculator {

    private static final int RATE_SCALE = 8;

    public BigDecimal calculate(
            BigDecimal peakAssetValue,
            BigDecimal currentAssetValue
    ) {
        requirePositive(
                peakAssetValue,
                "Peak asset value must be greater than zero"
        );
        requireNonNegative(
                currentAssetValue,
                "Current asset value must not be negative"
        );

        if (currentAssetValue.compareTo(peakAssetValue) >= 0) {
            return BigDecimal.ZERO.setScale(RATE_SCALE);
        }

        return peakAssetValue.subtract(currentAssetValue)
                .divide(peakAssetValue, MathContext.DECIMAL128)
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
