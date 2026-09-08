package com.ssafy.a107.trading.backtest.engine.execution;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class SlippageAmountCalculator {

    private static final int MONEY_SCALE = 8;

    public BigDecimal calculate(
            BigDecimal referencePrice,
            BigDecimal executedPrice,
            BigDecimal quantity
    ) {
        requirePositive(
                referencePrice,
                "Reference price must be greater than zero"
        );
        requirePositive(
                executedPrice,
                "Executed price must be greater than zero"
        );
        requirePositive(
                quantity,
                "Quantity must be greater than zero"
        );

        return executedPrice.subtract(referencePrice)
                .abs()
                .multiply(quantity)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private void requirePositive(
            BigDecimal value,
            String message
    ) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(message);
        }
    }
}
