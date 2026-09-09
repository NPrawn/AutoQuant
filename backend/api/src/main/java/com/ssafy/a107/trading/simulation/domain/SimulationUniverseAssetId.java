package com.ssafy.a107.trading.simulation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class SimulationUniverseAssetId implements Serializable {

    @Column(name = "simulation_id", nullable = false)
    private UUID simulationId;

    @Column(name = "rebalance_at", nullable = false)
    private Instant rebalanceAt;

    @Column(name = "asset_id", nullable = false)
    private UUID assetId;

    protected SimulationUniverseAssetId() {
    }

    public SimulationUniverseAssetId(UUID simulationId, Instant rebalanceAt, UUID assetId) {
        this.simulationId = Objects.requireNonNull(simulationId);
        this.rebalanceAt = Objects.requireNonNull(rebalanceAt);
        this.assetId = Objects.requireNonNull(assetId);
    }

    public UUID getSimulationId() { return simulationId; }
    public Instant getRebalanceAt() { return rebalanceAt; }
    public UUID getAssetId() { return assetId; }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof SimulationUniverseAssetId that)) return false;
        return Objects.equals(simulationId, that.simulationId)
                && Objects.equals(rebalanceAt, that.rebalanceAt)
                && Objects.equals(assetId, that.assetId);
    }

    @Override
    public int hashCode() { return Objects.hash(simulationId, rebalanceAt, assetId); }
}
