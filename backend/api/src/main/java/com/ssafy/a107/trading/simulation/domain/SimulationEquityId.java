package com.ssafy.a107.trading.simulation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class SimulationEquityId implements Serializable {

    @Column(name = "simulation_id", nullable = false)
    private UUID simulationId;

    @Column(name = "valuation_at", nullable = false)
    private Instant valuationAt;

    protected SimulationEquityId() {
    }

    public SimulationEquityId(UUID simulationId, Instant valuationAt) {
        this.simulationId = Objects.requireNonNull(simulationId);
        this.valuationAt = Objects.requireNonNull(valuationAt);
    }

    public UUID getSimulationId() { return simulationId; }
    public Instant getValuationAt() { return valuationAt; }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof SimulationEquityId that)) return false;
        return Objects.equals(simulationId, that.simulationId) && Objects.equals(valuationAt, that.valuationAt);
    }

    @Override
    public int hashCode() { return Objects.hash(simulationId, valuationAt); }
}
