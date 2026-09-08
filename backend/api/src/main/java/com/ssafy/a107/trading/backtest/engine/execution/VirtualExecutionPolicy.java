package com.ssafy.a107.trading.backtest.engine.execution;

import java.math.BigDecimal;

public record VirtualExecutionPolicy(
        BigDecimal feeRate,
        BigDecimal slippageBps,
        BigDecimal minimumOrderAmount,
        BigDecimal priceTickSize,
        BigDecimal quantityStepSize
) {

    private static final BigDecimal BPS_DIVISOR = BigDecimal.valueOf(10_000);

    public VirtualExecutionPolicy {
        feeRate = requireFeeRate(feeRate);
        slippageBps = requireSlippageBps(slippageBps);
        minimumOrderAmount = requirePositive(
                minimumOrderAmount,
                "Minimum order amount must be greater than zero"
        );
        priceTickSize = requirePositive(
                priceTickSize,
                "Price tick size must be greater than zero"
        );
        quantityStepSize = requirePositive(
                quantityStepSize,
                "Quantity step size must be greater than zero"
        );
    }

    private static BigDecimal requireSlippageBps(BigDecimal value) {
        requireNonNegative(value, "Slippage bps must not be negative");

        if (value.compareTo(BPS_DIVISOR) >= 0) {
            throw new IllegalArgumentException(
                    "Slippage bps must be less than 10000"
            );
        }
        return value;
    }

    private static BigDecimal requireFeeRate(BigDecimal value) {
        if (value == null
                || value.signum() < 0
                || value.compareTo(BigDecimal.ONE) >= 0) {
            throw new IllegalArgumentException(
                    "Fee rate must be between 0 and 1"
            );
        }
        return value;
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

    private static BigDecimal requirePositive(
            BigDecimal value,
            String message
    ) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
