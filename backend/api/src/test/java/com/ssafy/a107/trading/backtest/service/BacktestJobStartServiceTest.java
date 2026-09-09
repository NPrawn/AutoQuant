package com.ssafy.a107.trading.backtest.service;

import com.ssafy.a107.strategy.domain.Strategy;
import com.ssafy.a107.trading.backtest.domain.BacktestRun;
import com.ssafy.a107.trading.backtest.domain.BacktestRunStatus;
import com.ssafy.a107.trading.backtest.messaging.BacktestJobMessage;
import com.ssafy.a107.trading.backtest.repository.BacktestRunRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class BacktestJobStartServiceTest {

    private static final UUID JOB_ID = UUID.fromString("3b834542-0e00-4a9f-87a7-f7d8162e357d");
    private static final UUID BACKTEST_ID = UUID.fromString("9a36b372-5979-43aa-bc5e-dfae6d2eec23");
    private static final Long STRATEGY_ID = 1L;
    private static final Instant NOW = Instant.parse("2026-09-08T00:00:00Z");

    @Mock
    private BacktestRunRepository backtestRunRepository;

    @Mock
    private BacktestRun backtestRun;

    @Mock
    private Strategy strategy;

    @DisplayName("QUEUED 백테스트 작업을 RUNNING 상태로 시작한다")
    @Test
    void startQueuedBacktestJob() {
        BacktestJobStartService service = createService();
        BacktestJobMessage message = message();
        given(backtestRunRepository.findByBacktestIdForUpdate(BACKTEST_ID)).willReturn(Optional.of(backtestRun));
        given(backtestRun.getStrategy()).willReturn(strategy);
        given(strategy.getStrategyId()).willReturn(STRATEGY_ID);
        given(backtestRun.getMarketDataVersion()).willReturn("market-v1");
        given(backtestRun.getEngineVersion()).willReturn("engine-v1");
        given(backtestRun.getStatus()).willReturn(BacktestRunStatus.QUEUED);

        boolean started = service.start(message);

        assertThat(started).isTrue();
        then(backtestRun).should().markRunning(NOW);
    }

    @DisplayName("재전달된 실행 중 백테스트 작업을 다시 실행한다")
    @Test
    void restartRedeliveredRunningBacktestJob() {
        BacktestJobStartService service = createService();
        given(backtestRunRepository.findByBacktestIdForUpdate(BACKTEST_ID)).willReturn(Optional.of(backtestRun));
        given(backtestRun.getStrategy()).willReturn(strategy);
        given(strategy.getStrategyId()).willReturn(STRATEGY_ID);
        given(backtestRun.getMarketDataVersion()).willReturn("market-v1");
        given(backtestRun.getEngineVersion()).willReturn("engine-v1");
        given(backtestRun.getStatus()).willReturn(BacktestRunStatus.RUNNING);

        boolean started = service.start(message(), true);

        assertThat(started).isTrue();
        then(backtestRun).should(never()).markRunning(any(Instant.class));
    }

    @DisplayName("redelivery가 아닌 실행 중 백테스트 작업은 다시 시작하지 않는다")
    @Test
    void ignoreDuplicateRunningBacktestJob() {
        BacktestJobStartService service = createService();
        given(backtestRunRepository.findByBacktestIdForUpdate(BACKTEST_ID))
                .willReturn(Optional.of(backtestRun));
        given(backtestRun.getStrategy()).willReturn(strategy);
        given(strategy.getStrategyId()).willReturn(STRATEGY_ID);
        given(backtestRun.getMarketDataVersion()).willReturn("market-v1");
        given(backtestRun.getEngineVersion()).willReturn("engine-v1");
        given(backtestRun.getStatus()).willReturn(BacktestRunStatus.RUNNING);

        boolean started = service.start(message(), false);

        assertThat(started).isFalse();
        then(backtestRun).should(never()).markRunning(any(Instant.class));
    }

    @DisplayName("백테스트 실행 정보와 일치하지 않는 작업 메시지는 거절한다")
    @Test
    void rejectJobMessageThatDoesNotMatchBacktestRun() {
        BacktestJobStartService service = createService();
        given(backtestRunRepository.findByBacktestIdForUpdate(BACKTEST_ID)).willReturn(Optional.of(backtestRun));
        given(backtestRun.getStrategy()).willReturn(strategy);
        given(strategy.getStrategyId()).willReturn(STRATEGY_ID);
        given(backtestRun.getMarketDataVersion()).willReturn("market-v1");

        assertThatThrownBy(() -> service.start(new BacktestJobMessage(
                JOB_ID,
                BACKTEST_ID,
                STRATEGY_ID,
                "market-v2",
                "engine-v1"
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Backtest job message does not match backtest run");

        then(backtestRun).should(never()).markRunning(any(Instant.class));
    }

    private BacktestJobStartService createService() {
        return new BacktestJobStartService(
                backtestRunRepository,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private BacktestJobMessage message() {
        return new BacktestJobMessage(
                JOB_ID,
                BACKTEST_ID,
                STRATEGY_ID,
                "market-v1",
                "engine-v1"
        );
    }
}
