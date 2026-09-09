package com.ssafy.a107.trading.simulation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "simulation_result", schema = "trading")
public class SimulationResult {

    @Id
    @Column(name = "simulation_id", nullable = false)
    private UUID simulationId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "simulation_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_simulation_result_run"))
    private SimulationRun simulationRun;

    @Column(name = "as_of", nullable = false)
    private Instant asOf;

    @Column(name = "asset_value", nullable = false, precision = 24, scale = 8)
    private BigDecimal assetValue;

    @Column(name = "cumulative_return", nullable = false, precision = 14, scale = 8)
    private BigDecimal cumulativeReturn;

    @Column(name = "mdd", precision = 14, scale = 8)
    private BigDecimal mdd;

    @Column(name = "sharpe_ratio", precision = 14, scale = 8)
    private BigDecimal sharpeRatio;

    @Column(name = "volatility", precision = 14, scale = 8)
    private BigDecimal volatility;

    @Column(name = "win_rate", precision = 14, scale = 8)
    private BigDecimal winRate;

    @Column(name = "trade_count", nullable = false)
    private int tradeCount;

    @Column(name = "total_fee", nullable = false, precision = 24, scale = 8)
    private BigDecimal totalFee;

    protected SimulationResult() {
    }

    public SimulationResult(SimulationRun simulationRun, Instant asOf, BigDecimal assetValue,
                            BigDecimal cumulativeReturn, BigDecimal mdd, BigDecimal sharpeRatio,
                            BigDecimal volatility, BigDecimal winRate, int tradeCount, BigDecimal totalFee) {
        this.simulationRun = Objects.requireNonNull(simulationRun);
        update(asOf, assetValue, cumulativeReturn, mdd, sharpeRatio, volatility, winRate, tradeCount, totalFee);
    }

    public void update(Instant asOf, BigDecimal assetValue, BigDecimal cumulativeReturn, BigDecimal mdd,
                       BigDecimal sharpeRatio, BigDecimal volatility, BigDecimal winRate,
                       int tradeCount, BigDecimal totalFee) {
        this.asOf = Objects.requireNonNull(asOf);
        this.assetValue = requireNonNegative(assetValue, "Asset value must not be negative");
        this.cumulativeReturn = Objects.requireNonNull(cumulativeReturn);
        this.mdd = mdd;
        this.sharpeRatio = sharpeRatio;
        this.volatility = volatility;
        this.winRate = winRate;
        if (tradeCount < 0) throw new IllegalArgumentException("Trade count must not be negative");
        this.tradeCount = tradeCount;
        this.totalFee = requireNonNegative(totalFee, "Total fee must not be negative");
    }

    private static BigDecimal requireNonNegative(BigDecimal value, String message) {
        if (value == null || value.signum() < 0) throw new IllegalArgumentException(message);
        return value;
    }

    public UUID getSimulationId() { return simulationId; }
    public SimulationRun getSimulationRun() { return simulationRun; }
    public Instant getAsOf() { return asOf; }
    public BigDecimal getAssetValue() { return assetValue; }
    public BigDecimal getCumulativeReturn() { return cumulativeReturn; }
    public BigDecimal getMdd() { return mdd; }
    public BigDecimal getSharpeRatio() { return sharpeRatio; }
    public BigDecimal getVolatility() { return volatility; }
    public BigDecimal getWinRate() { return winRate; }
    public int getTradeCount() { return tradeCount; }
    public BigDecimal getTotalFee() { return totalFee; }
}
