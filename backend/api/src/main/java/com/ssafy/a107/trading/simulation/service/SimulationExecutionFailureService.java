package com.ssafy.a107.trading.simulation.service;

import com.ssafy.a107.trading.simulation.domain.SimulationRun;
import com.ssafy.a107.trading.simulation.domain.SimulationRunStatus;
import com.ssafy.a107.trading.simulation.repository.SimulationRunRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class SimulationExecutionFailureService {

    private final SimulationRunRepository simulationRunRepository;

    public SimulationExecutionFailureService(SimulationRunRepository simulationRunRepository) {
        this.simulationRunRepository = simulationRunRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(UUID simulationId, RuntimeException exception) {
        simulationRunRepository.findBySimulationIdForUpdate(simulationId)
                .filter(simulationRun -> simulationRun.getStatus() == SimulationRunStatus.CREATED
                        || simulationRun.getStatus() == SimulationRunStatus.RUNNING)
                .ifPresent(simulationRun -> simulationRun.fail(exception.getClass().getSimpleName()));
    }
}
