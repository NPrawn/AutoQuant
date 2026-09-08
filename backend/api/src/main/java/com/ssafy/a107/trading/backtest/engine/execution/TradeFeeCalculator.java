package com.ssafy.a107.trading.backtest.engine.execution;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class TradeFeeCalculator {

    private static final int MONEY_SCALE = 8;

    public BigDecimal calculate(
            BigDecimal notional,
            BigDecimal feeRate
    ) {
        requireNonNegative(
                notional,
                "Notional must not be negative"
        );
        requireNonNegative(
                feeRate,
                "Fee rate must not be negative"
        );

        return notional.multiply(feeRate)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
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
