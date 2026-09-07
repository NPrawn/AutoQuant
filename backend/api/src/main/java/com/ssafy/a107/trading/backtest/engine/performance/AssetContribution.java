package com.ssafy.a107.trading.backtest.engine.performance;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record AssetContribution(
        UUID assetId,
        BigDecimal profitLoss,
        BigDecimal contributionRate
) {

    public AssetContribution {
        assetId = Objects.requireNonNull(assetId);
        profitLoss = Objects.requireNonNull(profitLoss);
        contributionRate = Objects.requireNonNull(contributionRate);
    }
}
