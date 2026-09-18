package com.ssafy.a107.trading.simulation.service;

import com.ssafy.a107.trading.simulation.domain.SimulationRun;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * 모의투자 실행을 검증하기 위한 명령을 조율한다.
 */
@Service
@Transactional
public class SimulationDebugCommandService {

    private final SimulationRunReadService simulationRunReadService;
    private final SimulationCandleExecutionService simulationCandleExecutionService;

    public SimulationDebugCommandService(
            SimulationRunReadService simulationRunReadService,
            SimulationCandleExecutionService simulationCandleExecutionService
    ) {
        this.simulationRunReadService = simulationRunReadService;
        this.simulationCandleExecutionService = simulationCandleExecutionService;
    }

    public SimulationCandleExecutionService.ForceRebalanceOutcome forceRebalance(
            UUID memberId, UUID simulationId, Instant executionAt
    ) {
        SimulationRun simulationRun = simulationRunReadService.getSimulationRun(memberId, simulationId);
        return simulationCandleExecutionService.forceRebalance(simulationRun.getSimulationId(), executionAt);
    }
}
