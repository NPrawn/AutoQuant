package com.ssafy.a107.trading.simulation.service;

import com.ssafy.a107.common.exception.ApiErrorCode;
import com.ssafy.a107.common.exception.ApiException;
import com.ssafy.a107.strategy.domain.Strategy;
import com.ssafy.a107.strategy.repository.StrategyRepository;
import com.ssafy.a107.trading.simulation.domain.SimulationRun;
import com.ssafy.a107.trading.simulation.repository.SimulationRunRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
@Transactional
public class SimulationRunCommandService {

    private final SimulationRunRepository simulationRunRepository;
    private final StrategyRepository strategyRepository;

    public SimulationRunCommandService(SimulationRunRepository simulationRunRepository,
                                       StrategyRepository strategyRepository) {
        this.simulationRunRepository = simulationRunRepository;
        this.strategyRepository = strategyRepository;
    }

    public SimulationRun createSimulationRun(UUID memberId, CreateSimulationRunCommand command) {
        validate(command);
        Strategy strategy = strategyRepository
                .findByStrategyIdAndMemberMemberIdAndDeletedAtIsNull(command.strategyId(), memberId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.STRATEGY_NOT_FOUND));
        return simulationRunRepository.save(new SimulationRun(
                strategy.getMember(), strategy, command.initialCapital(), command.feeRate(),
                command.slippageBps(), command.executionPriceRule(), command.engineVersion()
        ));
    }

    public SimulationRun startSimulationRun(UUID memberId, UUID simulationId) {
        SimulationRun simulationRun = findForUpdate(memberId, simulationId);
        try {
            simulationRun.start(Instant.now());
            return simulationRun;
        } catch (IllegalStateException exception) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST);
        }
    }

    public SimulationRun stopSimulationRun(UUID memberId, UUID simulationId) {
        SimulationRun simulationRun = findForUpdate(memberId, simulationId);
        try {
            simulationRun.stop(Instant.now());
            return simulationRun;
        } catch (IllegalStateException exception) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST);
        }
    }

    private SimulationRun findForUpdate(UUID memberId, UUID simulationId) {
        SimulationRun simulationRun = simulationRunRepository.findBySimulationIdForUpdate(simulationId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND));
        if (!simulationRun.getMember().getMemberId().equals(memberId)) {
            throw new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND);
        }
        return simulationRun;
    }

    private void validate(CreateSimulationRunCommand command) {
        if (command == null || command.strategyId() == null || command.strategyId() <= 0
                || command.executionPriceRule() == null || isInvalidVersion(command.engineVersion())
                || command.initialCapital() == null || command.initialCapital().compareTo(BigDecimal.ZERO) <= 0
                || command.feeRate() == null || command.slippageBps() == null
                || command.feeRate().signum() < 0 || command.feeRate().compareTo(BigDecimal.ONE) >= 0
                || command.slippageBps().signum() < 0) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST);
        }
    }

    private boolean isInvalidVersion(String engineVersion) {
        return engineVersion == null || engineVersion.isBlank() || engineVersion.trim().length() > 50;
    }
}
