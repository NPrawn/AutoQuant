package com.ssafy.a107.trading.simulation.service;

import com.ssafy.a107.common.exception.ApiErrorCode;
import com.ssafy.a107.common.exception.ApiException;
import com.ssafy.a107.trading.simulation.domain.SimulationEquity;
import com.ssafy.a107.trading.simulation.domain.SimulationPosition;
import com.ssafy.a107.trading.simulation.domain.SimulationResult;
import com.ssafy.a107.trading.simulation.domain.SimulationRun;
import com.ssafy.a107.trading.simulation.domain.SimulationTrade;
import com.ssafy.a107.trading.simulation.repository.SimulationEquityRepository;
import com.ssafy.a107.trading.simulation.repository.SimulationPositionRepository;
import com.ssafy.a107.trading.simulation.repository.SimulationResultRepository;
import com.ssafy.a107.trading.simulation.repository.SimulationRunRepository;
import com.ssafy.a107.trading.simulation.repository.SimulationTradeRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class SimulationRunReadService {

    private final SimulationRunRepository simulationRunRepository;
    private final SimulationResultRepository simulationResultRepository;
    private final SimulationPositionRepository simulationPositionRepository;
    private final SimulationTradeRepository simulationTradeRepository;
    private final SimulationEquityRepository simulationEquityRepository;

    public SimulationRunReadService(SimulationRunRepository simulationRunRepository,
                                    SimulationResultRepository simulationResultRepository,
                                    SimulationPositionRepository simulationPositionRepository,
                                    SimulationTradeRepository simulationTradeRepository,
                                    SimulationEquityRepository simulationEquityRepository) {
        this.simulationRunRepository = simulationRunRepository;
        this.simulationResultRepository = simulationResultRepository;
        this.simulationPositionRepository = simulationPositionRepository;
        this.simulationTradeRepository = simulationTradeRepository;
        this.simulationEquityRepository = simulationEquityRepository;
    }

    public Page<SimulationRun> getSimulationRuns(UUID memberId, Pageable pageable) {
        return simulationRunRepository.findMemberRunsRunningFirst(memberId, pageable);
    }

    public SimulationRun getSimulationRun(UUID memberId, UUID simulationId) {
        return simulationRunRepository.findBySimulationIdAndMemberMemberId(simulationId, memberId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.SIMULATION_NOT_FOUND));
    }

    public List<SimulationPosition> getSimulationPositions(UUID memberId, UUID simulationId) {
        getSimulationRun(memberId, simulationId);
        return simulationPositionRepository.findAllBySimulationRunSimulationIdOrderByIdAssetIdAsc(simulationId);
    }

    public Page<SimulationTrade> getSimulationTrades(UUID memberId, UUID simulationId, Pageable pageable) {
        getSimulationRun(memberId, simulationId);
        return simulationTradeRepository.findAllBySimulationRunSimulationIdOrderByFilledAtAscTradeIdAsc(simulationId, pageable);
    }

    public Page<SimulationEquity> getSimulationEquities(UUID memberId, UUID simulationId, Pageable pageable) {
        getSimulationRun(memberId, simulationId);
        return simulationEquityRepository.findAllBySimulationRunSimulationIdOrderByIdValuationAtAsc(simulationId, pageable);
    }

    public SimulationResult getSimulationResult(UUID memberId, UUID simulationId) {
        getSimulationRun(memberId, simulationId);
        return simulationResultRepository.findById(simulationId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.SIMULATION_NOT_FOUND));
    }
}
