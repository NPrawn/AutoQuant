package com.ssafy.a107.trading.backtest.engine.execution;

import com.ssafy.a107.trading.backtest.domain.TradeSide;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

@Component
public class OrderPrecisionCalculator {

    public BigDecimal roundPrice(
            BigDecimal price,
            BigDecimal tickSize,
            TradeSide side
    ) {
        requirePositive(price, "Price must be greater than zero");
        requirePositive(tickSize, "Tick size must be greater than zero");
        Objects.requireNonNull(side);

        RoundingMode roundingMode = switch (side) {
            case BUY -> RoundingMode.CEILING;
            case SELL -> RoundingMode.FLOOR;
        };

        BigDecimal roundedPrice = roundToStep(
                price,
                tickSize,
                roundingMode
        );

        if (roundedPrice.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Rounded price must be greater than zero"
            );
        }
        return roundedPrice;
    }

    public BigDecimal roundQuantity(
            BigDecimal quantity,
            BigDecimal stepSize
    ) {
        requirePositive(quantity, "Quantity must be greater than zero");
        requirePositive(stepSize, "Step size must be greater than zero");

        return roundToStep(
                quantity,
                stepSize,
                RoundingMode.FLOOR
        );
    }

    private BigDecimal roundToStep(
            BigDecimal value,
            BigDecimal stepSize,
            RoundingMode roundingMode
    ) {
        return value.divide(stepSize, 0, roundingMode)
                .multiply(stepSize);
    }

    private BigDecimal requirePositive(
            BigDecimal value,
            String message
    ) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
