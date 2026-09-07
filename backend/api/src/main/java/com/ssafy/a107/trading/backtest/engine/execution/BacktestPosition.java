package com.ssafy.a107.trading.backtest.engine.execution;

import com.ssafy.a107.trading.backtest.domain.PositionSide;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record BacktestPosition(
        UUID assetId,
        PositionSide positionSide,
        BigDecimal quantity,
        BigDecimal averageEntryPrice
) {

    public BacktestPosition {
        assetId = Objects.requireNonNull(assetId);
        positionSide = Objects.requireNonNull(positionSide);
        quantity = requirePositive(
                quantity,
                "Position quantity must be greater than zero"
        );
        averageEntryPrice = requirePositive(
                averageEntryPrice,
                "Average entry price must be greater than zero"
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
}
