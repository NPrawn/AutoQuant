package com.ssafy.a107.trading.simulation.service;

import com.ssafy.a107.strategy.domain.dsl.RebalancePeriod;
import com.ssafy.a107.strategy.domain.dsl.Timeframe;
import com.ssafy.a107.trading.simulation.domain.SimulationRebalanceAttemptStatus;
import com.ssafy.a107.trading.simulation.domain.SimulationRunStatus;
import com.ssafy.a107.trading.simulation.repository.SimulationRunRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

class SimulationDailyRebalanceRecoverySchedulerTest {

    private final SimulationRunRepository simulationRunRepository = mock(SimulationRunRepository.class);
    private final SimulationCandleExecutionService simulationCandleExecutionService = mock(SimulationCandleExecutionService.class);
    private final SimulationDailyRebalanceRecoveryScheduler scheduler = new SimulationDailyRebalanceRecoveryScheduler(
            simulationRunRepository, simulationCandleExecutionService
    );

    @Test
    @DisplayName("09시 2분 뒤에는 미처리 일일 모의투자를 복구한다")
    void recoverMissedDailyRebalancesAfterDataIsConfirmed() {
        Instant now = Instant.parse("2026-09-18T00:02:00Z");
        Instant executionAt = Instant.parse("2026-09-18T00:00:00Z");
        UUID simulationId = UUID.randomUUID();
        given(simulationRunRepository.findSimulationIdsDueForRebalanceRecovery(
                eq(SimulationRunStatus.RUNNING), eq(Timeframe.ONE_DAY), eq(RebalancePeriod.DAILY),
                eq(executionAt), any(Pageable.class)
        )).willReturn(List.of(simulationId));
        given(simulationCandleExecutionService.recoverDailyRebalance(simulationId, executionAt)).willReturn(
                new SimulationCandleExecutionService.DailyRecoveryOutcome(
                        simulationId, executionAt, SimulationRebalanceAttemptStatus.COMPLETED
                )
        );

        scheduler.recoverMissedDailyRebalances(now);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        then(simulationRunRepository).should().findSimulationIdsDueForRebalanceRecovery(
                eq(SimulationRunStatus.RUNNING), eq(Timeframe.ONE_DAY), eq(RebalancePeriod.DAILY),
                eq(executionAt), pageable.capture()
        );
        then(simulationCandleExecutionService).should().recoverDailyRebalance(simulationId, executionAt);
        org.assertj.core.api.Assertions.assertThat(pageable.getValue().getPageNumber()).isZero();
        org.assertj.core.api.Assertions.assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    @DisplayName("09시 2분 전에는 아직 일일 모의투자 복구를 시작하지 않는다")
    void doNotRecoverBeforeDataIsConfirmed() {
        scheduler.recoverMissedDailyRebalances(Instant.parse("2026-09-18T00:01:59Z"));

        then(simulationRunRepository).shouldHaveNoInteractions();
        then(simulationCandleExecutionService).shouldHaveNoInteractions();
    }
}
