package com.ssafy.a107.trading.simulation.controller.response;

import com.ssafy.a107.trading.simulation.domain.SimulationRun;
import org.springframework.data.domain.Page;

import java.util.List;

public record SimulationRunPageResponse(List<SimulationRunResponse> content, int page, int size,
                                        long totalElements, int totalPages) {
    public static SimulationRunPageResponse from(Page<SimulationRun> simulationRuns) {
        return new SimulationRunPageResponse(
                simulationRuns.getContent().stream().map(SimulationRunResponse::from).toList(),
                simulationRuns.getNumber(), simulationRuns.getSize(), simulationRuns.getTotalElements(),
                simulationRuns.getTotalPages()
        );
    }
}
