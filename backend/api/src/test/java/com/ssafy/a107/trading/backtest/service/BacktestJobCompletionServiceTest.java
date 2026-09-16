package com.ssafy.a107.trading.backtest.service;

import com.ssafy.a107.trading.backtest.domain.BacktestRun;
import com.ssafy.a107.trading.backtest.domain.BacktestRunStatus;
import com.ssafy.a107.trading.backtest.engine.BacktestExecutionResult;
import com.ssafy.a107.trading.backtest.engine.performance.CostComparison;
import com.ssafy.a107.trading.backtest.engine.performance.EquitySnapshot;
import com.ssafy.a107.trading.backtest.engine.performance.PerformanceReport;
import com.ssafy.a107.trading.backtest.engine.performance.TradeStatistics;
import com.ssafy.a107.member.domain.Member;
import com.ssafy.a107.notification.domain.NotificationType;
import com.ssafy.a107.notification.service.NotificationRequestedEvent;
import com.ssafy.a107.strategy.domain.Strategy;
import com.ssafy.a107.trading.backtest.repository.BacktestRunRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

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
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class BacktestJobCompletionServiceTest {

    private static final UUID BACKTEST_ID = UUID.fromString("2bfb9f4e-6994-4d3d-90cd-6250bf5ab2b1");
    private static final Instant NOW = Instant.parse("2026-09-08T00:00:00Z");
    private static final UUID MEMBER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final String STRATEGY_NAME = "이평선 골든크로스";

    @Mock
    private BacktestRunRepository backtestRunRepository;

    @Mock
    private BacktestResultPersistenceService backtestResultPersistenceService;

    @Mock
    private BacktestRun backtestRun;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @DisplayName("백테스트 작업을 완료 상태로 전이한 후 결과를 저장한다")
    @Test
    void completeBacktestJobBeforePersistingResult() {
        BacktestExecutionResult executionResult = executionResult();
        given(backtestRunRepository.findByBacktestIdForUpdate(BACKTEST_ID))
                .willReturn(Optional.of(backtestRun));
        given(backtestRun.getStatus()).willReturn(BacktestRunStatus.RUNNING);
        givenNotifiableRun();

        boolean completed = service().complete(BACKTEST_ID, executionResult);

        assertThat(completed).isTrue();
        InOrder inOrder = inOrder(
                backtestRun,
                backtestResultPersistenceService
        );
        then(backtestRun).should(inOrder).complete(NOW);
        then(backtestResultPersistenceService).should(inOrder).persist(
                backtestRun,
                executionResult.performanceReport(),
                executionResult.fills(),
                executionResult.assets(),
                executionResult.universeAssets()
        );
    }

    @DisplayName("결과 저장에 실패하면 예외를 전파한다")
    @Test
    void propagatePersistenceFailureAfterCompletingBacktestJob() {
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

        then(backtestRun).should().complete(NOW);
        /* 결과가 없는데 끝났다고 알리면 사용자가 열어도 볼 것이 없다. */
        then(applicationEventPublisher).shouldHaveNoInteractions();
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

    @DisplayName("완료를 알리는 알림은 결과를 저장한 뒤에 낸다")
    @Test
    void publishCompletionNotificationAfterPersistingResult() {
        given(backtestRunRepository.findByBacktestIdForUpdate(BACKTEST_ID))
                .willReturn(Optional.of(backtestRun));
        given(backtestRun.getStatus()).willReturn(BacktestRunStatus.RUNNING);
        givenNotifiableRun();

        service().complete(BACKTEST_ID, executionResult());

        InOrder inOrder = inOrder(backtestResultPersistenceService, applicationEventPublisher);
        then(backtestResultPersistenceService).should(inOrder)
                .persist(any(), any(), anyList(), anyMap(), anyList());
        ArgumentCaptor<NotificationRequestedEvent> event = ArgumentCaptor.forClass(NotificationRequestedEvent.class);
        then(applicationEventPublisher).should(inOrder).publishEvent(event.capture());
        assertThat(event.getValue().memberId()).isEqualTo(MEMBER_ID);
        assertThat(event.getValue().notificationType()).isEqualTo(NotificationType.BACKTEST_COMPLETED);
        assertThat(event.getValue().title()).contains(STRATEGY_NAME);
        assertThat(event.getValue().body()).contains("2024-01-01").contains("2024-06-30");
        assertThat(event.getValue().targetId()).isEqualTo(BACKTEST_ID.toString());
    }

    /* 알림 문구는 전략 이름과 소유자, 평가 기간을 읽는다. 그 경로까지 가는 시험에서만 준비한다. */
    private void givenNotifiableRun() {
        Member member = mock(Member.class);
        Strategy strategy = mock(Strategy.class);
        given(member.getMemberId()).willReturn(MEMBER_ID);
        given(strategy.getName()).willReturn(STRATEGY_NAME);
        given(backtestRun.getMember()).willReturn(member);
        given(backtestRun.getStrategy()).willReturn(strategy);
        given(backtestRun.getBacktestId()).willReturn(BACKTEST_ID);
        given(backtestRun.getPeriodStart()).willReturn(Instant.parse("2024-01-01T00:00:00Z"));
        given(backtestRun.getPeriodEnd()).willReturn(Instant.parse("2024-06-30T00:00:00Z"));
    }

    private BacktestJobCompletionService service() {
        return new BacktestJobCompletionService(
                backtestRunRepository,
                backtestResultPersistenceService,
                applicationEventPublisher,
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
