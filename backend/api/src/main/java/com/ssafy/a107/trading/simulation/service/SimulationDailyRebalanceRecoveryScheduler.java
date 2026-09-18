package com.ssafy.a107.trading.simulation.service;

import com.ssafy.a107.strategy.domain.dsl.RebalancePeriod;
import com.ssafy.a107.strategy.domain.dsl.Timeframe;
import com.ssafy.a107.trading.simulation.domain.SimulationRebalanceAttemptStatus;
import com.ssafy.a107.trading.simulation.domain.SimulationRunStatus;
import com.ssafy.a107.trading.simulation.repository.SimulationRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

@Component
public class SimulationDailyRebalanceRecoveryScheduler {

    private static final Logger log = LoggerFactory.getLogger(SimulationDailyRebalanceRecoveryScheduler.class);
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private static final int BATCH_SIZE = 100;

    private final SimulationRunRepository simulationRunRepository;
    private final SimulationCandleExecutionService simulationCandleExecutionService;

    public SimulationDailyRebalanceRecoveryScheduler(
            SimulationRunRepository simulationRunRepository,
            SimulationCandleExecutionService simulationCandleExecutionService
    ) {
        this.simulationRunRepository = simulationRunRepository;
        this.simulationCandleExecutionService = simulationCandleExecutionService;
    }

    @Scheduled(fixedDelayString = "${simulation.daily-recovery.poll-interval:PT1M}")
    public void recoverMissedDailyRebalances() {
        recoverMissedDailyRebalances(Instant.now());
    }

    void recoverMissedDailyRebalances(Instant now) {
        Instant executionAt = LocalDate.ofInstant(now, KOREA).atTime(9, 0).atZone(KOREA).toInstant();
        if (now.isBefore(executionAt.plusSeconds(120))) return;

        for (int page = 0; ; page++) {
            var simulationIds = simulationRunRepository.findSimulationIdsDueForRebalanceRecovery(
                    SimulationRunStatus.RUNNING, Timeframe.ONE_DAY, RebalancePeriod.DAILY, executionAt,
                    PageRequest.of(page, BATCH_SIZE)
            );
            for (UUID simulationId : simulationIds) {
                var outcome = simulationCandleExecutionService.recoverDailyRebalance(simulationId, executionAt);
                if (outcome.status() == SimulationRebalanceAttemptStatus.WAITING_FOR_DATA) {
                    log.info("Daily simulation rebalance recovery waits: simulationId={}, executionAt={}",
                            simulationId, executionAt);
                }
            }
            if (simulationIds.size() < BATCH_SIZE) return;
        }
    }
}
