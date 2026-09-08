package com.ssafy.a107.trading.backtest.engine.portfolio;

import com.ssafy.a107.trading.backtest.domain.PositionSide;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.UUID;

public record BacktestPosition(
        UUID assetId,
        PositionSide positionSide,
        BigDecimal quantity,
        BigDecimal averageEntryPrice,
        BigDecimal costBasis
) {

    private static final int MONEY_SCALE = 8;

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
        costBasis = requirePositive(
                costBasis,
                "Cost basis must be greater than zero"
        );
    }

    public BacktestPosition(
            UUID assetId,
            PositionSide positionSide,
            BigDecimal quantity,
            BigDecimal averageEntryPrice
    ) {
        this(
                assetId,
                positionSide,
                quantity,
                averageEntryPrice,
                averageEntryPrice.multiply(quantity)
                        .setScale(MONEY_SCALE, RoundingMode.HALF_UP)
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
