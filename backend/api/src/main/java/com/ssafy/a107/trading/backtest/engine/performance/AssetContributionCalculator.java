package com.ssafy.a107.trading.backtest.engine.performance;

import com.ssafy.a107.trading.backtest.domain.PositionSide;
import com.ssafy.a107.trading.backtest.engine.portfolio.BacktestPosition;
import com.ssafy.a107.trading.backtest.engine.portfolio.RealizedTradeResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

@Component
public class AssetContributionCalculator {

    private static final int MONEY_SCALE = 8;
    private static final int RATE_SCALE = 8;

    public List<AssetContribution> calculate(
            BigDecimal initialCapital,
            List<RealizedTradeResult> realizedTrades,
            List<BacktestPosition> openPositions,
            Map<UUID, BigDecimal> finalPrices
    ) {
        requirePositive(
                initialCapital,
                "Initial capital must be greater than zero"
        );
        Objects.requireNonNull(realizedTrades);
        Objects.requireNonNull(openPositions);
        Objects.requireNonNull(finalPrices);

        Map<UUID, BigDecimal> profitLossByAsset = new TreeMap<>();

        for (RealizedTradeResult realizedTrade : realizedTrades) {
            if (realizedTrade == null) {
                throw new IllegalArgumentException(
                        "Realized trade must not be null"
                );
            }
            profitLossByAsset.merge(
                    realizedTrade.assetId(),
                    realizedTrade.realizedProfitLoss(),
                    BigDecimal::add
            );
        }

        for (BacktestPosition position : openPositions) {
            if (position == null) {
                throw new IllegalArgumentException(
                        "Open position must not be null"
                );
            }
            if (position.positionSide() != PositionSide.LONG) {
                throw new IllegalArgumentException(
                        "Only long positions are supported"
                );
            }

            BigDecimal finalPrice = finalPrices.get(position.assetId());
            requirePositive(
                    finalPrice,
                    "Final price must be greater than zero"
            );

            BigDecimal unrealizedProfitLoss = finalPrice
                    .multiply(position.quantity())
                    .subtract(position.costBasis())
                    .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

            profitLossByAsset.merge(
                    position.assetId(),
                    unrealizedProfitLoss,
                    BigDecimal::add
            );
        }

        List<AssetContribution> contributions = new ArrayList<>();
        for (Map.Entry<UUID, BigDecimal> entry
                : profitLossByAsset.entrySet()) {
            BigDecimal profitLoss = entry.getValue()
                    .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            BigDecimal contributionRate = profitLoss.divide(
                            initialCapital,
                            MathContext.DECIMAL128
                    )
                    .setScale(RATE_SCALE, RoundingMode.HALF_UP);

            contributions.add(new AssetContribution(
                    entry.getKey(),
                    profitLoss,
                    contributionRate
            ));
        }

        return List.copyOf(contributions);
    }

    private void requirePositive(BigDecimal value, String message) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(message);
        }
    }
}
