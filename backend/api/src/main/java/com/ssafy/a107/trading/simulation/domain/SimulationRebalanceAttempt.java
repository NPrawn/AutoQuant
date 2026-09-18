package com.ssafy.a107.trading.simulation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "simulation_rebalance_attempt", schema = "trading")
public class SimulationRebalanceAttempt {

    @EmbeddedId
    private SimulationRebalanceAttemptId id;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private SimulationRebalanceAttemptStatus status;

    @Column(name = "reason", length = 100)
    private String reason;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SimulationRebalanceAttempt() {
    }

    public SimulationRebalanceAttempt(SimulationRebalanceAttemptId id, SimulationRebalanceAttemptStatus status, String reason) {
        this.id = Objects.requireNonNull(id);
        update(status, reason);
    }

    public void update(SimulationRebalanceAttemptStatus status, String reason) {
        this.status = Objects.requireNonNull(status);
        this.reason = reason;
        this.updatedAt = Instant.now();
    }

    public SimulationRebalanceAttemptId getId() { return id; }
    public SimulationRebalanceAttemptStatus getStatus() { return status; }
    public String getReason() { return reason; }
    public Instant getUpdatedAt() { return updatedAt; }
}
