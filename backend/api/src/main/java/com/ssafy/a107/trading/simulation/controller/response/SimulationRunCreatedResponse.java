package com.ssafy.a107.trading.simulation.controller.response;

import com.ssafy.a107.trading.simulation.domain.SimulationRun;
import com.ssafy.a107.trading.simulation.domain.SimulationRunStatus;

import java.util.UUID;

public record SimulationRunCreatedResponse(UUID simulationId, SimulationRunStatus status) {
    public static SimulationRunCreatedResponse from(SimulationRun simulationRun) {
        return new SimulationRunCreatedResponse(simulationRun.getSimulationId(), simulationRun.getStatus());
    }
}
