package com.ssafy.a107.trading.backtest.engine.portfolio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.UUID;

public record RealizedTradeResult(
        UUID assetId,
        BigDecimal quantity,
        BigDecimal netProceeds,
        BigDecimal allocatedCostBasis
) {

    private static final int MONEY_SCALE = 8;

    public RealizedTradeResult {
        assetId = Objects.requireNonNull(assetId);
        quantity = requirePositive(
                quantity,
                "Realized quantity must be greater than zero"
        );
        netProceeds = requireNonNegative(
                netProceeds,
                "Net proceeds must not be negative"
        );
        allocatedCostBasis = requirePositive(
                allocatedCostBasis,
                "Allocated cost basis must be greater than zero"
        );
    }

    public BigDecimal realizedProfitLoss() {
        return netProceeds.subtract(allocatedCostBasis)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
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
