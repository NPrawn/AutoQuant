package com.ssafy.a107.trading.backtest.engine.portfolio;

import com.ssafy.a107.trading.backtest.domain.PositionSide;
import com.ssafy.a107.trading.backtest.domain.TradeSide;
import com.ssafy.a107.trading.backtest.engine.execution.VirtualFill;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

public class BacktestPortfolio {

    private static final int MONEY_SCALE = 8;
    private static final int PRICE_SCALE = 8;

    private BigDecimal cashBalance;
    private final Map<UUID, BacktestPosition> positions = new TreeMap<>();

    public BacktestPortfolio(BigDecimal initialCash) {
        this.cashBalance = requirePositive(
                initialCash,
                "Initial cash must be greater than zero"
        );
    }

    public void applyBuy(VirtualFill fill) {
        Objects.requireNonNull(fill);

        if (fill.order().side() != TradeSide.BUY) {
            throw new IllegalArgumentException(
                    "Only buy fills can be applied as a buy"
            );
        }

        if (fill.order().positionSide() != PositionSide.LONG) {
            throw new IllegalArgumentException(
                    "Only long positions are supported"
            );
        }

        BigDecimal requiredCash = fill.notional()
                .add(fill.feeAmount())
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        if (cashBalance.compareTo(requiredCash) < 0) {
            throw new IllegalStateException(
                    "Cash balance is insufficient for buy fill"
            );
        }

        UUID assetId = fill.order().assetId();
        BacktestPosition currentPosition = positions.get(assetId);
        BigDecimal updatedQuantity = fill.quantity();
        BigDecimal updatedAverageEntryPrice = fill.executedPrice()
                .setScale(PRICE_SCALE, RoundingMode.HALF_UP);
        BigDecimal updatedCostBasis = requiredCash;

        if (currentPosition != null) {
            updatedQuantity = currentPosition.quantity()
                    .add(fill.quantity());

            BigDecimal currentCost = currentPosition.averageEntryPrice()
                    .multiply(currentPosition.quantity());
            BigDecimal addedCost = fill.executedPrice()
                    .multiply(fill.quantity());

            updatedAverageEntryPrice = currentCost.add(addedCost)
                    .divide(updatedQuantity, MathContext.DECIMAL128)
                    .setScale(PRICE_SCALE, RoundingMode.HALF_UP);
            updatedCostBasis = currentPosition.costBasis()
                    .add(requiredCash)
                    .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }

        positions.put(
                assetId,
                new BacktestPosition(
                        assetId,
                        PositionSide.LONG,
                        updatedQuantity,
                        updatedAverageEntryPrice,
                        updatedCostBasis
                )
        );

        cashBalance = cashBalance.subtract(requiredCash)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    public void applySell(VirtualFill fill) {
        Objects.requireNonNull(fill);

        if (fill.order().side() != TradeSide.SELL) {
            throw new IllegalArgumentException(
                    "Only sell fills can be applied as a sell"
            );
        }

        if (fill.order().positionSide() != PositionSide.LONG) {
            throw new IllegalArgumentException(
                    "Only long positions are supported"
            );
        }

        UUID assetId = fill.order().assetId();
        BacktestPosition currentPosition = positions.get(assetId);

        if (currentPosition == null) {
            throw new IllegalStateException(
                    "Position does not exist for sell fill"
            );
        }

        if (fill.quantity().compareTo(currentPosition.quantity()) > 0) {
            throw new IllegalStateException(
                    "Sell quantity exceeds position quantity"
            );
        }

        BigDecimal proceeds = fill.notional()
                .subtract(fill.feeAmount())
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        if (proceeds.signum() < 0) {
            throw new IllegalArgumentException(
                    "Sell fee must not exceed notional"
            );
        }

        BigDecimal updatedQuantity = currentPosition.quantity()
                .subtract(fill.quantity());

        if (updatedQuantity.signum() == 0) {
            positions.remove(assetId);
        } else {
            positions.put(
                    assetId,
                    new BacktestPosition(
                            assetId,
                            PositionSide.LONG,
                            updatedQuantity,
                            currentPosition.averageEntryPrice()
                    )
            );
        }

        cashBalance = cashBalance.add(proceeds)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    public BigDecimal getCashBalance() {
        return cashBalance;
    }

    public List<BacktestPosition> getPositions() {
        return List.copyOf(positions.values());
    }

    public Optional<BacktestPosition> findPosition(UUID assetId) {
        return Optional.ofNullable(
                positions.get(Objects.requireNonNull(assetId))
        );
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
