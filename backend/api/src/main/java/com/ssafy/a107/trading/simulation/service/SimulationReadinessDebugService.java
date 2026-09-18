package com.ssafy.a107.trading.simulation.service;

import com.ssafy.a107.asset.service.CollectedAssetReadService;
import com.ssafy.a107.trading.backtest.engine.marketdata.BacktestWarmupCandleCountResolver;
import com.ssafy.a107.trading.backtest.engine.marketdata.HistoricalCandleProvider;
import com.ssafy.a107.trading.simulation.domain.SimulationRun;
import com.ssafy.a107.trading.simulation.repository.SimulationPositionRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Profile("simulation-debug")
@ConditionalOnProperty(prefix = "simulation.debug", name = "enabled", havingValue = "true")
@Service
@Transactional(readOnly = true)
public class SimulationReadinessDebugService {

    private final SimulationRunReadService simulationRunReadService;
    private final SimulationPositionRepository simulationPositionRepository;
    private final CollectedAssetReadService collectedAssetReadService;
    private final SimulationStrategyReadinessEvaluator readinessEvaluator;

    public SimulationReadinessDebugService(
            SimulationRunReadService simulationRunReadService,
            SimulationPositionRepository simulationPositionRepository,
            CollectedAssetReadService collectedAssetReadService,
            HistoricalCandleProvider historicalCandleProvider,
            BacktestWarmupCandleCountResolver warmupCandleCountResolver
    ) {
        this.simulationRunReadService = simulationRunReadService;
        this.simulationPositionRepository = simulationPositionRepository;
        this.collectedAssetReadService = collectedAssetReadService;
        this.readinessEvaluator = new SimulationStrategyReadinessEvaluator(
                historicalCandleProvider, warmupCandleCountResolver
        );
    }

    public SimulationStrategyReadinessEvaluator.Evaluation inspect(
            UUID memberId, UUID simulationId, Instant executionAt, boolean hideLatestCompletedCandle
    ) {
        SimulationRun simulationRun = simulationRunReadService.getSimulationRun(memberId, simulationId);
        Set<UUID> heldAssetIds = simulationPositionRepository
                .findAllBySimulationRunSimulationIdOrderByIdAssetIdAsc(simulationId)
                .stream()
                .map(position -> position.getAsset().getAssetId())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return readinessEvaluator.evaluate(
                simulationRun.getStrategy().getStrategyDsl(),
                simulationRun.getTimeframe(),
                collectedAssetReadService.findAvailableAt(
                        com.ssafy.a107.asset.domain.Exchange.UPBIT,
                        simulationRun.getQuoteCurrency(),
                        executionAt.minus(java.time.Duration.ofMinutes(simulationRun.getTimeframe().getIntervalMinutes()))
                ),
                heldAssetIds,
                executionAt,
                hideLatestCompletedCandle
        );
    }
}
