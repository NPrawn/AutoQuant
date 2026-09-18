package com.ssafy.a107.trading.simulation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.Objects;

@Entity
@Table(name = "simulation_equity", schema = "trading")
public class SimulationEquity {

    @EmbeddedId
    private SimulationEquityId id;

    @MapsId("simulationId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "simulation_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_simulation_equity_run"))
    private SimulationRun simulationRun;

    @Column(name = "total_asset_value", nullable = false, precision = 24, scale = 8)
    private BigDecimal totalAssetValue;

    @Column(name = "cash_balance", nullable = false, precision = 24, scale = 8)
    private BigDecimal cashBalance;

    @Column(name = "return_rate", nullable = false, precision = 14, scale = 8)
    private BigDecimal returnRate;

    @Column(name = "drawdown_rate", nullable = false, precision = 14, scale = 8)
    private BigDecimal drawdownRate;

    protected SimulationEquity() {
    }

    public SimulationEquity(SimulationRun simulationRun, SimulationEquityId id, BigDecimal totalAssetValue,
                            BigDecimal cashBalance, BigDecimal returnRate, BigDecimal drawdownRate) {
        this.simulationRun = Objects.requireNonNull(simulationRun);
        this.id = Objects.requireNonNull(id);
        this.totalAssetValue = requireNonNegative(totalAssetValue, "Total asset value must not be negative");
        this.cashBalance = requireNonNegative(cashBalance, "Cash balance must not be negative");
        this.returnRate = Objects.requireNonNull(returnRate);
        this.drawdownRate = Objects.requireNonNull(drawdownRate);
    }

    private static BigDecimal requireNonNegative(BigDecimal value, String message) {
        if (value == null || value.signum() < 0) throw new IllegalArgumentException(message);
        return value;
    }

    public SimulationEquityId getId() { return id; }
    public SimulationRun getSimulationRun() { return simulationRun; }
    public BigDecimal getTotalAssetValue() { return totalAssetValue; }
    public BigDecimal getCashBalance() { return cashBalance; }
    public BigDecimal getReturnRate() { return returnRate; }
    public BigDecimal getDrawdownRate() { return drawdownRate; }
}
