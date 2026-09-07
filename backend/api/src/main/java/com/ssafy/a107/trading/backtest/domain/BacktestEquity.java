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
        this.totalAssetValue = Objects.requireNonNull(totalAssetValue);
        this.cashBalance = Objects.requireNonNull(cashBalance);
        this.returnRate = Objects.requireNonNull(returnRate);
        this.drawdownRate = Objects.requireNonNull(drawdownRate);
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
