package com.ssafy.a107.trading.simulation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class SimulationRebalanceAttemptId implements Serializable {

    @Column(name = "simulation_id", nullable = false)
    private UUID simulationId;

    @Column(name = "execution_at", nullable = false)
    private Instant executionAt;

    protected SimulationRebalanceAttemptId() {
    }

    public SimulationRebalanceAttemptId(UUID simulationId, Instant executionAt) {
        this.simulationId = Objects.requireNonNull(simulationId);
        this.executionAt = Objects.requireNonNull(executionAt);
    }

    public UUID getSimulationId() { return simulationId; }
    public Instant getExecutionAt() { return executionAt; }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof SimulationRebalanceAttemptId that)) return false;
        return Objects.equals(simulationId, that.simulationId) && Objects.equals(executionAt, that.executionAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(simulationId, executionAt);
    }
}
