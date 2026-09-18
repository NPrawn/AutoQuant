package com.ssafy.a107.trading.simulation.domain;

import com.ssafy.a107.trading.backtest.domain.PositionSide;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class SimulationPositionId implements Serializable {

    @Column(name = "simulation_id", nullable = false)
    private UUID simulationId;

    @Column(name = "asset_id", nullable = false)
    private UUID assetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "position_side", nullable = false, length = 10)
    private PositionSide positionSide;

    protected SimulationPositionId() {
    }

    public SimulationPositionId(UUID simulationId, UUID assetId, PositionSide positionSide) {
        this.simulationId = Objects.requireNonNull(simulationId);
        this.assetId = Objects.requireNonNull(assetId);
        this.positionSide = Objects.requireNonNull(positionSide);
    }

    public UUID getSimulationId() { return simulationId; }
    public UUID getAssetId() { return assetId; }
    public PositionSide getPositionSide() { return positionSide; }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof SimulationPositionId that)) return false;
        return Objects.equals(simulationId, that.simulationId)
                && Objects.equals(assetId, that.assetId)
                && positionSide == that.positionSide;
    }

    @Override
    public int hashCode() {
        return Objects.hash(simulationId, assetId, positionSide);
    }
}
