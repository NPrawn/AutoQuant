package com.ssafy.a107.trading.backtest.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.Objects;

@Entity
@Table(name = "backtest_equity", schema = "trading")
public class BacktestEquity {

    @EmbeddedId
    private BacktestEquityId id;

    @MapsId("backtestId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "backtest_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_backtest_equity_run")
    )
    private BacktestRun backtestRun;

    @Column(
            name = "total_asset_value",
            nullable = false,
            precision = 24,
            scale = 8,
            updatable = false
    )
    private BigDecimal totalAssetValue;

    @Column(
            name = "cash_balance",
            nullable = false,
            precision = 24,
            scale = 8,
            updatable = false
    )
    private BigDecimal cashBalance;

    @Column(
            name = "return_rate",
            nullable = false,
            precision = 14,
            scale = 8,
            updatable = false
    )
    private BigDecimal returnRate;

    @Column(
            name = "drawdown_rate",
            nullable = false,
            precision = 14,
            scale = 8,
            updatable = false
    )
    private BigDecimal drawdownRate;

    protected BacktestEquity() {
    }

    public BacktestEquity(
            BacktestEquityId id,
            BacktestRun backtestRun,
            BigDecimal totalAssetValue,
            BigDecimal cashBalance,
            BigDecimal returnRate,
            BigDecimal drawdownRate
    ) {
        this.id = Objects.requireNonNull(id);
        this.backtestRun = Objects.requireNonNull(backtestRun);
        this.totalAssetValue = requireNonNegative(
                totalAssetValue,
                "Total asset value must not be negative"
        );
        this.cashBalance = requireNonNegative(
                cashBalance,
                "Cash balance must not be negative"
        );
        this.returnRate = requireAtLeastNegativeOne(returnRate);
        this.drawdownRate = requireRate(drawdownRate);

        if (this.cashBalance.compareTo(this.totalAssetValue) > 0) {
            throw new IllegalArgumentException(
                    "Cash balance must not exceed total asset value"
            );
        }
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

    private static BigDecimal requireAtLeastNegativeOne(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ONE.negate()) < 0) {
            throw new IllegalArgumentException(
                    "Return rate must not be less than -1"
            );
        }
        return value;
    }

    private static BigDecimal requireRate(BigDecimal value) {
        if (value == null
                || value.signum() < 0
                || value.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException(
                    "Drawdown rate must be between 0 and 1"
            );
        }
        return value;
    }

    public BacktestEquityId getId() {
        return id;
    }

    public BacktestRun getBacktestRun() {
        return backtestRun;
    }

    public BigDecimal getTotalAssetValue() {
        return totalAssetValue;
    }

    public BigDecimal getCashBalance() {
        return cashBalance;
    }

    public BigDecimal getReturnRate() {
        return returnRate;
    }

    public BigDecimal getDrawdownRate() {
        return drawdownRate;
    }
}
