package com.ssafy.a107.trading.backtest.engine.performance;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class ExcessReturnCalculator {

    private static final int RATE_SCALE = 8;

    public BigDecimal calculate(
            BigDecimal strategyReturn,
            BigDecimal benchmarkReturn
    ) {
        if (strategyReturn == null || benchmarkReturn == null) {
            throw new IllegalArgumentException(
                    "Strategy and benchmark returns must not be null"
            );
        }

        return strategyReturn.subtract(benchmarkReturn)
                .setScale(RATE_SCALE, RoundingMode.HALF_UP);
    }
}
