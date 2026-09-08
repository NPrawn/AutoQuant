package com.ssafy.a107.trading.backtest.domain;

import com.ssafy.a107.asset.domain.Asset;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "backtest_trade", schema = "trading")
public class BacktestTrade {

    private static final int MAX_REASON_LENGTH = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "trade_id", nullable = false, updatable = false)
    private UUID tradeId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "backtest_id",
            nullable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_backtest_trade_run")
    )
    private BacktestRun backtestRun;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "asset_id",
            nullable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_backtest_trade_asset")
    )
    private Asset asset;

    @Enumerated(EnumType.STRING)
    @Column(name = "position_side", nullable = false, length = 10, updatable = false)
    private PositionSide positionSide;

    @Enumerated(EnumType.STRING)
    @Column(name = "side", nullable = false, length = 4, updatable = false)
    private TradeSide side;

    @Column(name = "signal_at", nullable = false, updatable = false)
    private Instant signalAt;

    @Column(name = "filled_at", nullable = false, updatable = false)
    private Instant filledAt;

    @Column(
            name = "executed_price",
            nullable = false,
            precision = 24,
            scale = 8,
            updatable = false
    )
    private BigDecimal executedPrice;

    @Column(
            name = "quantity",
            nullable = false,
            precision = 24,
            scale = 8,
            updatable = false
    )
    private BigDecimal quantity;

    @Column(
            name = "notional",
            nullable = false,
            precision = 24,
            scale = 8,
            updatable = false
    )
    private BigDecimal notional;

    @Column(
            name = "fee_amount",
            nullable = false,
            precision = 24,
            scale = 8,
            updatable = false
    )
    private BigDecimal feeAmount;

    @Column(
            name = "slippage_amount",
            nullable = false,
            precision = 24,
            scale = 8,
            updatable = false
    )
    private BigDecimal slippageAmount;

    @Column(name = "reason", length = MAX_REASON_LENGTH, updatable = false)
    private String reason;

    protected BacktestTrade() {
    }

    public BacktestTrade(
            BacktestRun backtestRun,
            Asset asset,
            PositionSide positionSide,
            TradeSide side,
            Instant signalAt,
            Instant filledAt,
            BigDecimal executedPrice,
            BigDecimal quantity,
            BigDecimal notional,
            BigDecimal feeAmount,
            BigDecimal slippageAmount,
            String reason
    ) {
        this.backtestRun = Objects.requireNonNull(backtestRun);
        this.asset = Objects.requireNonNull(asset);
        this.positionSide = Objects.requireNonNull(positionSide);
        this.side = Objects.requireNonNull(side);
        this.signalAt = Objects.requireNonNull(signalAt);
        this.filledAt = requireFilledAfterSignal(signalAt, filledAt);
        this.executedPrice = requirePositive(
                executedPrice,
                "Executed price must be greater than zero"
        );
        this.quantity = requirePositive(quantity, "Quantity must be greater than zero");
        this.notional = requireNonNegative(notional, "Notional must not be negative");
        this.feeAmount = requireNonNegative(feeAmount, "Fee amount must not be negative");
        this.slippageAmount = requireNonNegative(
                slippageAmount,
                "Slippage amount must not be negative"
        );
        this.reason = normalizeReason(reason);
    }

    private static Instant requireFilledAfterSignal(
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

    private static BigDecimal requireNonNegative(
            BigDecimal value,
            String message
    ) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private static String normalizeReason(String reason) {
        if (reason == null) {
            return null;
        }

        String normalizedReason = reason.trim();
        if (normalizedReason.length() > MAX_REASON_LENGTH) {
            throw new IllegalArgumentException(
                    "Reason must not exceed 100 characters"
            );
        }
        return normalizedReason.isEmpty() ? null : normalizedReason;
    }

    public UUID getTradeId() {
        return tradeId;
    }

    public BacktestRun getBacktestRun() {
        return backtestRun;
    }

    public Asset getAsset() {
        return asset;
    }

    public PositionSide getPositionSide() {
        return positionSide;
    }

    public TradeSide getSide() {
        return side;
    }

    public Instant getSignalAt() {
        return signalAt;
    }

    public Instant getFilledAt() {
        return filledAt;
    }

    public BigDecimal getExecutedPrice() {
        return executedPrice;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getNotional() {
        return notional;
    }

    public BigDecimal getFeeAmount() {
        return feeAmount;
    }

    public BigDecimal getSlippageAmount() {
        return slippageAmount;
    }

    public String getReason() {
        return reason;
    }
}
