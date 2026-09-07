package com.ssafy.a107.trading.backtest.engine.execution;

import java.math.BigDecimal;
import java.util.Objects;

public record VirtualFill(
        VirtualOrder order,
        BigDecimal executedPrice,
        BigDecimal quantity,
        BigDecimal notional,
        BigDecimal feeAmount,
        BigDecimal slippageAmount
) {
    public VirtualFill {
        order = Objects.requireNonNull(order);
        executedPrice = requirePositive(
                executedPrice,
                "Executed price must be greater than zero"
        );
        quantity = requirePositive(
                quantity,
                "Quantity must be greater than zero"
        );
        notional = requirePositive(
                notional,
                "Notional must be greater than zero"
                );
        feeAmount = requireNonNegative(
                feeAmount,
                "Fee amount must not be negative"
        );
        slippageAmount = requireNonNegative(
                slippageAmount,
                "Slippage amount must not be negative"
        );
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

    private static BigDecimal requireNonNegative(
            BigDecimal value,
            String message
    ) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
