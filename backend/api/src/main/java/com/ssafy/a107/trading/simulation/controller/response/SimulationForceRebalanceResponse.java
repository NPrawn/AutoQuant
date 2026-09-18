package com.ssafy.a107.trading.simulation.controller.response;

import com.ssafy.a107.trading.simulation.service.SimulationCandleExecutionService;

import java.time.Instant;
import java.util.UUID;

public record SimulationForceRebalanceResponse(
        UUID simulationId,
        String state,
        Instant executionAt,
        Instant lastCandleAt,
        Instant lastRebalanceAt
) {
    public static SimulationForceRebalanceResponse from(
            SimulationCandleExecutionService.ForceRebalanceOutcome outcome
    ) {
        return new SimulationForceRebalanceResponse(
                outcome.simulationId(),
                outcome.state().name(),
                outcome.executionAt(),
                outcome.lastCandleAt(),
                outcome.lastRebalanceAt()
        );
    }
}
