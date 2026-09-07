package com.ssafy.a107.trading.backtest.engine.performance;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class BenchmarkReturnCalculator {

    private final CumulativeReturnCalculator cumulativeReturnCalculator;

    public BenchmarkReturnCalculator(
            CumulativeReturnCalculator cumulativeReturnCalculator
    ) {
        this.cumulativeReturnCalculator = cumulativeReturnCalculator;
    }

    public BigDecimal calculate(
            BigDecimal initialBenchmarkPrice,
            BigDecimal finalBenchmarkPrice
    ) {
        requirePositive(
                initialBenchmarkPrice,
                "Initial benchmark price must be greater than zero"
        );
        requirePositive(
                finalBenchmarkPrice,
                "Final benchmark price must be greater than zero"
        );

        return cumulativeReturnCalculator.calculate(
                initialBenchmarkPrice,
                finalBenchmarkPrice
        );
    }

    private void requirePositive(BigDecimal value, String message) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(message);
        }
    }
}
