package com.ssafy.a107.trading.backtest.engine.performance;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

@Component
public class VolatilityCalculator {

    private static final int RATE_SCALE = 8;
    private static final MathContext MATH_CONTEXT = MathContext.DECIMAL128;

    public BigDecimal calculateAnnualized(
            List<BigDecimal> periodicReturns,
            int periodsPerYear
    ) {
        Objects.requireNonNull(periodicReturns);

        if (periodicReturns.size() < 2) {
            throw new IllegalArgumentException(
                    "At least two periodic returns are required"
            );
        }

        if (periodsPerYear <= 0) {
            throw new IllegalArgumentException(
                    "Periods per year must be greater than zero"
            );
        }

        BigDecimal mean = calculateMean(periodicReturns);
        BigDecimal variance = calculatePopulationVariance(
                periodicReturns,
                mean
        );
        BigDecimal standardDeviation = variance.sqrt(MATH_CONTEXT);
        BigDecimal annualizationFactor = BigDecimal.valueOf(periodsPerYear)
                .sqrt(MATH_CONTEXT);

        return standardDeviation.multiply(
                        annualizationFactor,
                        MATH_CONTEXT
                )
                .setScale(RATE_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateMean(List<BigDecimal> periodicReturns) {
        BigDecimal sum = BigDecimal.ZERO;

        for (BigDecimal periodicReturn : periodicReturns) {
            requireValidReturn(periodicReturn);
            sum = sum.add(periodicReturn);
        }

        return sum.divide(
                BigDecimal.valueOf(periodicReturns.size()),
                MATH_CONTEXT
        );
    }

    private BigDecimal calculatePopulationVariance(
            List<BigDecimal> periodicReturns,
            BigDecimal mean
    ) {
        BigDecimal squaredDeviationSum = BigDecimal.ZERO;

        for (BigDecimal periodicReturn : periodicReturns) {
            BigDecimal deviation = periodicReturn.subtract(mean);
            squaredDeviationSum = squaredDeviationSum.add(
                    deviation.multiply(deviation, MATH_CONTEXT)
            );
        }

        return squaredDeviationSum.divide(
                BigDecimal.valueOf(periodicReturns.size()),
                MATH_CONTEXT
        );
    }

    private void requireValidReturn(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ONE.negate()) < 0) {
            throw new IllegalArgumentException(
                    "Periodic return must not be less than -1"
            );
        }
    }
}