package com.ssafy.a107.trading.backtest.engine.portfolio;

import com.ssafy.a107.strategy.engine.portfolio.TargetAssetWeight;
import com.ssafy.a107.strategy.engine.portfolio.TargetPortfolio;
import com.ssafy.a107.trading.backtest.domain.TradeSide;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

@Component
public class BacktestRebalanceOrderPlanner {

    private static final int MONEY_SCALE = 8;

    private final PortfolioValuationCalculator portfolioValuationCalculator;

    public BacktestRebalanceOrderPlanner(
            PortfolioValuationCalculator portfolioValuationCalculator
    ) {
        this.portfolioValuationCalculator = Objects.requireNonNull(
                portfolioValuationCalculator
        );
    }

    public List<BacktestRebalanceOrder> plan(
            BacktestPortfolio portfolio,
            TargetPortfolio targetPortfolio,
            Map<UUID, BigDecimal> currentPrices
    ) {
        Objects.requireNonNull(portfolio);
        Objects.requireNonNull(targetPortfolio);
        Objects.requireNonNull(currentPrices);

        BigDecimal totalAssetValue = portfolioValuationCalculator
                .calculateTotalAssetValue(portfolio, currentPrices);
        Map<UUID, BigDecimal> targetWeightsByAssetId = targetWeightsByAssetId(
                targetPortfolio
        );
        Map<UUID, BigDecimal> currentNotionalsByAssetId =
                currentNotionalsByAssetId(portfolio, currentPrices);

        TreeSet<UUID> assetIds = new TreeSet<>();
        assetIds.addAll(targetWeightsByAssetId.keySet());
        assetIds.addAll(currentNotionalsByAssetId.keySet());

        List<BacktestRebalanceOrder> sellOrders = new ArrayList<>();
        List<BacktestRebalanceOrder> buyOrders = new ArrayList<>();

        for (UUID assetId : assetIds) {
            BigDecimal targetNotional = totalAssetValue
                    .multiply(targetWeightsByAssetId.getOrDefault(
                            assetId,
                            BigDecimal.ZERO
                    ))
                    .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            BigDecimal currentNotional = currentNotionalsByAssetId
                    .getOrDefault(assetId, BigDecimal.ZERO);
            BigDecimal notionalDifference = targetNotional.subtract(
                    currentNotional
            );

            if (notionalDifference.signum() < 0) {
                sellOrders.add(new BacktestRebalanceOrder(
                        assetId,
                        TradeSide.SELL,
                        notionalDifference.abs()
                ));
            }

            if (notionalDifference.signum() > 0) {
                buyOrders.add(new BacktestRebalanceOrder(
                        assetId,
                        TradeSide.BUY,
                        notionalDifference
                ));
            }
        }

        List<BacktestRebalanceOrder> orders = new ArrayList<>();
        orders.addAll(sellOrders);
        orders.addAll(buyOrders);

        return List.copyOf(orders);
    }

    private Map<UUID, BigDecimal> targetWeightsByAssetId(
            TargetPortfolio targetPortfolio
    ) {
        Map<UUID, BigDecimal> targetWeightsByAssetId = new TreeMap<>();

        for (TargetAssetWeight targetAssetWeight
                : targetPortfolio.assetWeights()) {
            UUID assetId = Objects.requireNonNull(
                    targetAssetWeight.assetId()
            );
            BigDecimal targetWeight = requireNonNegative(
                    targetAssetWeight.targetWeight(),
                    "Target weight must not be negative"
            );

            if (targetWeightsByAssetId.put(assetId, targetWeight) != null) {
                throw new IllegalArgumentException(
                        "Target portfolio must not contain duplicate assets"
                );
            }
        }

        return targetWeightsByAssetId;
    }

    private Map<UUID, BigDecimal> currentNotionalsByAssetId(
            BacktestPortfolio portfolio,
            Map<UUID, BigDecimal> currentPrices
    ) {
        Map<UUID, BigDecimal> currentNotionalsByAssetId = new TreeMap<>();

        for (BacktestPosition position : portfolio.getPositions()) {
            BigDecimal currentPrice = requirePositive(
                    currentPrices.get(position.assetId()),
                    "Current price must be greater than zero"
            );
            BigDecimal currentNotional = currentPrice
                    .multiply(position.quantity())
                    .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

            currentNotionalsByAssetId.put(
                    position.assetId(),
                    currentNotional
            );
        }

        return currentNotionalsByAssetId;
    }

    private BigDecimal requireNonNegative(
            BigDecimal value,
            String message
    ) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException(message);
        }

        return value;
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