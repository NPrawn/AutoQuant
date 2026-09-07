package com.ssafy.a107.trading.backtest.engine.execution;

import com.ssafy.a107.trading.backtest.domain.PositionSide;
import com.ssafy.a107.trading.backtest.domain.TradeSide;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record VirtualOrder(
        UUID assetId,
        PositionSide positionSide,
        TradeSide side,
        Instant signalAt,
        Instant filledAt,
        BigDecimal referencePrice,
        BigDecimal requestedNotional
) {

    public VirtualOrder {
        assetId = Objects.requireNonNull(assetId);
        positionSide = Objects.requireNonNull(positionSide);
        side = Objects.requireNonNull(side);
        signalAt = Objects.requireNonNull(signalAt);
        filledAt = requireAfterSignal(signalAt, filledAt);
        referencePrice = requirePositive(
                referencePrice,
                "Reference price must be greater than zero"
        );
        requestedNotional = requirePositive(
                requestedNotional,
                "Requested notional must be greater than zero"
        );
    }

    private static Instant requireAfterSignal(
            Instant signalAt,
            Instant filledAt
    ) {
        if (filledAt == null || !signalAt.isBefore(filledAt)) {
            throw new IllegalArgumentException(
                    "Filled at must be after signal at"
            );
        }
        return filledAt;
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
