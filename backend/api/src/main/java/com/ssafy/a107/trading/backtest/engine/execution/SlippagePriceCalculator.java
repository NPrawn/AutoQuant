package com.ssafy.a107.trading.backtest.engine.execution;

import com.ssafy.a107.trading.backtest.domain.TradeSide;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Objects;

@Component
public class SlippagePriceCalculator {

    private static final BigDecimal BPS_DIVISOR = BigDecimal.valueOf(10_000);

    public BigDecimal calculate(
            BigDecimal referencePrice,
            BigDecimal slippageBps,
            TradeSide side
    ) {
        requirePositive(
                referencePrice,
                "Reference price must be greater than zero"
        );
        requireValidSlippageBps(slippageBps);
        Objects.requireNonNull(side);

        BigDecimal slippageRate = slippageBps.divide(
                BPS_DIVISOR,
                MathContext.DECIMAL128
        );

        BigDecimal multiplier = switch (side) {
            case BUY -> BigDecimal.ONE.add(slippageRate);
            case SELL -> BigDecimal.ONE.subtract(slippageRate);
        };

        return referencePrice.multiply(
                multiplier,
                MathContext.DECIMAL128
        );
    }

    private void requireValidSlippageBps(BigDecimal value) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException(
                    "Slippage bps must not be negative"
            );
        }

        if (value.compareTo(BPS_DIVISOR) >= 0) {
            throw new IllegalArgumentException(
                    "Slippage bps must be less than 10000"
            );
        }
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
