package com.ssafy.a107.trading.backtest.engine.performance;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

@Component
public class CagrCalculator {

    private static final int RATE_SCALE = 8;
    private static final MathContext MATH_CONTEXT = MathContext.DECIMAL128;
    private static final BigDecimal SECONDS_PER_YEAR =
            BigDecimal.valueOf(365L * 24 * 60 * 60);

    public BigDecimal calculate(
            BigDecimal initialAssetValue,
            BigDecimal finalAssetValue,
            Instant periodStart,
            Instant periodEnd
    ) {
        requirePositive(
                initialAssetValue,
                "Initial asset value must be greater than zero"
        );
        requireNonNegative(
                finalAssetValue,
                "Final asset value must not be negative"
        );
        Objects.requireNonNull(periodStart);
        Objects.requireNonNull(periodEnd);

        if (!periodEnd.isAfter(periodStart)) {
            throw new IllegalArgumentException(
                    "Period end must be after period start"
            );
        }

        if (finalAssetValue.signum() == 0) {
            return BigDecimal.ONE.negate()
                    .setScale(RATE_SCALE, RoundingMode.HALF_UP);
        }

        BigDecimal elapsedSeconds = calculateElapsedSeconds(
                periodStart,
                periodEnd
        );
        BigDecimal annualizationExponent = SECONDS_PER_YEAR.divide(
                elapsedSeconds,
                MATH_CONTEXT
        );
        BigDecimal growthFactor = finalAssetValue.divide(
                initialAssetValue,
                MATH_CONTEXT
        );

        double annualizedGrowth = StrictMath.pow(
                growthFactor.doubleValue(),
                annualizationExponent.doubleValue()
        );

        if (!Double.isFinite(annualizedGrowth)) {
            throw new ArithmeticException(
                    "Annualized growth exceeds the supported range"
            );
        }

        return BigDecimal.valueOf(annualizedGrowth)
                .subtract(BigDecimal.ONE)
                .setScale(RATE_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateElapsedSeconds(
            Instant periodStart,
            Instant periodEnd
    ) {
        Duration duration = Duration.between(periodStart, periodEnd);

        return BigDecimal.valueOf(duration.getSeconds())
                .add(
                        BigDecimal.valueOf(
                                duration.getNano(),
                                9
                        )
                );
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