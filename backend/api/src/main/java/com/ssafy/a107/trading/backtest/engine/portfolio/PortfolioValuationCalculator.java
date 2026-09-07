package com.ssafy.a107.trading.backtest.engine.portfolio;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Component
public class PortfolioValuationCalculator {

    private static final int MONEY_SCALE = 8;

    public BigDecimal calculateTotalAssetValue(
            BacktestPortfolio portfolio,
            Map<UUID, BigDecimal> currentPrices
    ) {
        Objects.requireNonNull(portfolio);
        Objects.requireNonNull(currentPrices);

        BigDecimal totalAssetValue = portfolio.getCashBalance();

        for (BacktestPosition position : portfolio.getPositions()) {
            BigDecimal currentPrice = requireCurrentPrice(
                    position.assetId(),
                    currentPrices
            );

            BigDecimal positionValue = currentPrice.multiply(
                    position.quantity()
            );
            totalAssetValue = totalAssetValue.add(positionValue);
        }

        return totalAssetValue.setScale(
                MONEY_SCALE,
                RoundingMode.HALF_UP
        );
    }

    private BigDecimal requireCurrentPrice(
            UUID assetId,
            Map<UUID, BigDecimal> currentPrices
    ) {
        BigDecimal currentPrice = currentPrices.get(assetId);

        if (currentPrice == null || currentPrice.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Current price must be greater than zero"
            );
        }

        return currentPrice;
    }
}
