package com.ssafy.a107.trading.simulation.domain;

import com.ssafy.a107.asset.domain.Asset;
import com.ssafy.a107.trading.backtest.domain.PositionSide;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import jakarta.persistence.Column;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "simulation_position", schema = "trading")
public class SimulationPosition {

    @EmbeddedId
    private SimulationPositionId id;

    @MapsId("simulationId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "simulation_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_simulation_position_run"))
    private SimulationRun simulationRun;

    @MapsId("assetId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_simulation_position_asset"))
    private Asset asset;

    @Column(name = "quantity", nullable = false, precision = 24, scale = 8)
    private BigDecimal quantity;

    @Column(name = "average_entry_price", nullable = false, precision = 24, scale = 8)
    private BigDecimal averageEntryPrice;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SimulationPosition() {
    }

    public SimulationPosition(SimulationRun simulationRun, Asset asset, PositionSide positionSide,
                              BigDecimal quantity, BigDecimal averageEntryPrice, Instant updatedAt) {
        this.simulationRun = Objects.requireNonNull(simulationRun);
        this.asset = Objects.requireNonNull(asset);
        this.id = new SimulationPositionId(simulationRun.getSimulationId(), asset.getAssetId(), positionSide);
        change(quantity, averageEntryPrice, updatedAt);
    }

    public void change(BigDecimal quantity, BigDecimal averageEntryPrice, Instant updatedAt) {
        if (quantity == null || quantity.signum() <= 0 || averageEntryPrice == null || averageEntryPrice.signum() <= 0) {
            throw new IllegalArgumentException("Position quantity and average entry price must be positive");
        }
        this.quantity = quantity;
        this.averageEntryPrice = averageEntryPrice;
        this.updatedAt = Objects.requireNonNull(updatedAt);
    }

    public SimulationPositionId getId() { return id; }
    public SimulationRun getSimulationRun() { return simulationRun; }
    public Asset getAsset() { return asset; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getAverageEntryPrice() { return averageEntryPrice; }
    public Instant getUpdatedAt() { return updatedAt; }
}
