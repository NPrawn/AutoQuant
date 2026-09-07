package com.ssafy.a107.trading.backtest.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "backtest_result", schema = "trading")
public class BacktestResult {

    @Id
    @Column(name = "backtest_id", nullable = false, updatable = false)
    private UUID backtestId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "backtest_id",
            nullable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_backtest_result_run")
    )
    private BacktestRun backtestRun;

    @Column(
            name = "final_asset_value",
            nullable = false,
            precision = 24,
            scale = 8,
            updatable = false
    )
    private BigDecimal finalAssetValue;

    @Column(
            name = "cumulative_return",
            nullable = false,
            precision = 14,
            scale = 8,
            updatable = false
    )
    private BigDecimal cumulativeReturn;

    @Column(
            name = "cagr",
            nullable = false,
            precision = 14,
            scale = 8,
            updatable = false
    )
    private BigDecimal cagr;

    @Column(
            name = "mdd",
            nullable = false,
            precision = 14,
            scale = 8,
            updatable = false
    )
    private BigDecimal mdd;

    @Column(
            name = "sharpe_ratio",
            precision = 14,
            scale = 8,
            updatable = false
    )
    private BigDecimal sharpeRatio;

    @Column(
            name = "volatility",
            precision = 14,
            scale = 8,
            updatable = false
    )
    private BigDecimal volatility;

    @Column(
            name = "win_rate",
            precision = 14,
            scale = 8,
            updatable = false
    )
    private BigDecimal winRate;

    @Column(name = "trade_count", nullable = false, updatable = false)
    private int tradeCount;

    @Column(
            name = "total_fee",
            nullable = false,
            precision = 24,
            scale = 8,
            updatable = false
    )
    private BigDecimal totalFee;

    @Column(
            name = "benchmark_return",
            precision = 14,
            scale = 8,
            updatable = false
    )
    private BigDecimal benchmarkReturn;

    protected BacktestResult() {

    }

    public BacktestResult(
            BacktestRun backtestRun,
            BigDecimal finalAssetValue,
            BigDecimal cumulativeReturn,
            BigDecimal cagr,
            BigDecimal mdd,
            BigDecimal sharpeRatio,
            BigDecimal volatility,
            BigDecimal winRate,
            int tradeCount,
            BigDecimal totalFee,
            BigDecimal benchmarkReturn
    ) {
        this.backtestRun = requireCompleted(backtestRun);
        this.finalAssetValue = requireNonNegative(
                finalAssetValue,
                "Final asset value must not be negative"
        );
        this.cumulativeReturn = Objects.requireNonNull(cumulativeReturn);
        this.cagr = Objects.requireNonNull(cagr);
        this.mdd = Objects.requireNonNull(mdd);
        this.sharpeRatio = sharpeRatio;
        this.volatility = volatility;
        this.winRate = winRate;
        this.tradeCount = requireNonNegative(
                tradeCount,
                "Trade count must not be negative"
        );
        this.totalFee = requireNonNegative(
                totalFee,
                "Total fee must not be negative"
        );
        this.benchmarkReturn = benchmarkReturn;
    }

    private static BacktestRun requireCompleted(BacktestRun backtestRun) {
        Objects.requireNonNull(backtestRun);

        if (backtestRun.getStatus() != BacktestRunStatus.COMPLETED) {
            throw new IllegalArgumentException(
                    "Backtest result requires a completed backtest run"
            );
        }
        return backtestRun;
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

    private static int requireNonNegative(int value, String message) {
        if (value < 0) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    public UUID getBacktestId() {
        return backtestId;
    }

    public BacktestRun getBacktestRun() {
        return backtestRun;
    }

    public BigDecimal getFinalAssetValue() {
        return finalAssetValue;
    }

    public BigDecimal getCumulativeReturn() {
        return cumulativeReturn;
    }

    public BigDecimal getCagr() {
        return cagr;
    }

    public BigDecimal getMdd() {
        return mdd;
    }

    public BigDecimal getSharpeRatio() {
        return sharpeRatio;
    }

    public BigDecimal getVolatility() {
        return volatility;
    }

    public BigDecimal getWinRate() {
        return winRate;
    }

    public int getTradeCount() {
        return tradeCount;
    }

    public BigDecimal getTotalFee() {
        return totalFee;
    }

    public BigDecimal getBenchmarkReturn() {
        return benchmarkReturn;
    }
}
