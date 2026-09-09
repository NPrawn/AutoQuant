package com.ssafy.a107.trading.backtest.service;

import com.ssafy.a107.trading.backtest.domain.BacktestRun;
import com.ssafy.a107.trading.backtest.domain.BacktestRunStatus;
import com.ssafy.a107.trading.backtest.engine.BacktestExecutionResult;
import com.ssafy.a107.trading.backtest.engine.performance.CostComparison;
import com.ssafy.a107.trading.backtest.engine.performance.EquitySnapshot;
import com.ssafy.a107.trading.backtest.engine.performance.PerformanceReport;
import com.ssafy.a107.trading.backtest.engine.performance.TradeStatistics;
import com.ssafy.a107.trading.backtest.repository.BacktestRunRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class BacktestJobCompletionServiceTest {

    private static final UUID BACKTEST_ID = UUID.fromString("2bfb9f4e-6994-4d3d-90cd-6250bf5ab2b1");
    private static final Instant NOW = Instant.parse("2026-09-08T00:00:00Z");

    @Mock
    private BacktestRunRepository backtestRunRepository;

    @Mock
    private BacktestResultPersistenceService backtestResultPersistenceService;

    @Mock
    private BacktestRun backtestRun;

    @DisplayName("결과 저장 후 백테스트 작업을 완료 상태로 전이한다")
    @Test
    void completeBacktestJobAfterPersistingResult() {
        BacktestExecutionResult executionResult = executionResult();
        given(backtestRunRepository.findByBacktestIdForUpdate(BACKTEST_ID))
                .willReturn(Optional.of(backtestRun));
        given(backtestRun.getStatus()).willReturn(BacktestRunStatus.RUNNING);

        boolean completed = service().complete(BACKTEST_ID, executionResult);

        assertThat(completed).isTrue();
        then(backtestResultPersistenceService).should().persist(
                backtestRun,
                executionResult.performanceReport(),
                executionResult.fills(),
                executionResult.assets(),
                executionResult.universeAssets()
        );
        then(backtestRun).should().complete(NOW);
    }

    @DisplayName("결과 저장에 실패하면 백테스트 작업을 완료 처리하지 않는다")
    @Test
    void doNotCompleteBacktestJobWhenPersistingResultFails() {
        BacktestExecutionResult executionResult = executionResult();
        given(backtestRunRepository.findByBacktestIdForUpdate(BACKTEST_ID))
                .willReturn(Optional.of(backtestRun));
        given(backtestRun.getStatus()).willReturn(BacktestRunStatus.RUNNING);
        given(backtestResultPersistenceService.persist(
                any(BacktestRun.class),
                any(PerformanceReport.class),
                anyList(),
                anyMap(),
                anyList()
        )).willThrow(new IllegalStateException("Result persistence failed"));

        assertThatThrownBy(() -> service().complete(BACKTEST_ID, executionResult))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Result persistence failed");

        then(backtestRun).should(never()).complete(any(Instant.class));
    }

    @DisplayName("이미 완료된 백테스트 작업은 결과를 중복 저장하지 않는다")
    @Test
    void skipAlreadyCompletedBacktestJob() {
        given(backtestRunRepository.findByBacktestIdForUpdate(BACKTEST_ID))
                .willReturn(Optional.of(backtestRun));
        given(backtestRun.getStatus()).willReturn(BacktestRunStatus.COMPLETED);

        boolean completed = service().complete(BACKTEST_ID, executionResult());

        assertThat(completed).isFalse();
        then(backtestResultPersistenceService).shouldHaveNoInteractions();
        then(backtestRun).should(never()).complete(any(Instant.class));
    }

    @DisplayName("존재하지 않는 백테스트 작업은 결과를 저장하지 않는다")
    @Test
    void rejectMissingBacktestRun() {
        given(backtestRunRepository.findByBacktestIdForUpdate(BACKTEST_ID))
                .willReturn(Optional.empty());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service().complete(BACKTEST_ID, executionResult()))
                .withMessage("Backtest run not found");

        then(backtestResultPersistenceService).shouldHaveNoInteractions();
    }

    private BacktestJobCompletionService service() {
        return new BacktestJobCompletionService(
                backtestRunRepository,
                backtestResultPersistenceService,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private BacktestExecutionResult executionResult() {
        return new BacktestExecutionResult(
                new PerformanceReport(
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        new TradeStatistics(0, BigDecimal.ZERO, BigDecimal.ZERO),
                        new CostComparison(
                                BigDecimal.ZERO,
                                BigDecimal.ZERO,
                                BigDecimal.ZERO,
                                BigDecimal.ZERO,
                                BigDecimal.ZERO,
                                BigDecimal.ZERO,
                                BigDecimal.ZERO
                        ),
                        List.of(),
                        List.of(new EquitySnapshot(
                                NOW,
                                BigDecimal.ZERO,
                                BigDecimal.ZERO,
                                BigDecimal.ZERO,
                                BigDecimal.ZERO
                        ))
                ),
                List.of(),
                Map.of()
        );
    }
}
