package com.ssafy.a107.trading.backtest.engine.portfolio;

import com.ssafy.a107.trading.backtest.domain.TradeSide;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record BacktestRebalanceOrder(
        UUID assetId,
        TradeSide side,
        BigDecimal requestedNotional
) {

    public BacktestRebalanceOrder {
        assetId = Objects.requireNonNull(assetId);
        side = Objects.requireNonNull(side);

        if (requestedNotional == null || requestedNotional.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Requested notional must be greater than zero"
            );
        }
    }
}
