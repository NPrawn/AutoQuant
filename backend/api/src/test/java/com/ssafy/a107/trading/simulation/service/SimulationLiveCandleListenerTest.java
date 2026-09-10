package com.ssafy.a107.trading.simulation.service;

import com.ssafy.a107.realtime.domain.LiveMinuteCandle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.TaskScheduler;

import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

class SimulationLiveCandleListenerTest {

    @DisplayName("실시간 Candle 수신은 실행 작업을 별도 Executor에 위임한다")
    @Test
    void delegateLiveCandleToExecutor() {
        SimulationCandleExecutionService executionService = mock(SimulationCandleExecutionService.class);
        List<Runnable> submittedTasks = new ArrayList<>();
        TaskExecutor taskExecutor = submittedTasks::add;
        SimulationLiveCandleListener listener = new SimulationLiveCandleListener(
                executionService, taskExecutor, mock(TaskScheduler.class)
        );
        LiveMinuteCandle candle = candle();

        listener.onLiveMinuteCandle(candle);

        then(executionService).shouldHaveNoInteractions();
        submittedTasks.getFirst().run();
        then(executionService).should().handle(candle);
    }

    @DisplayName("동일한 진행 Candle은 실행 큐에 한 번만 등록한다")
    @Test
    void enqueueSameLiveCandleOnlyOnce() {
        SimulationCandleExecutionService executionService = mock(SimulationCandleExecutionService.class);
        List<Runnable> submittedTasks = new ArrayList<>();
        SimulationLiveCandleListener listener = new SimulationLiveCandleListener(
                executionService, submittedTasks::add, mock(TaskScheduler.class)
        );
        LiveMinuteCandle candle = candle();

        listener.onLiveMinuteCandle(candle);
        listener.onLiveMinuteCandle(candle);

        org.assertj.core.api.Assertions.assertThat(submittedTasks).hasSize(1);
    }

    @DisplayName("일시 실행 실패 Candle은 별도 스케줄러로 재시도한다")
    @Test
    void scheduleRetryWhenExecutionRequestsRetry() {
        SimulationCandleExecutionService executionService = mock(SimulationCandleExecutionService.class);
        TaskScheduler retryScheduler = mock(TaskScheduler.class);
        SimulationLiveCandleListener listener = new SimulationLiveCandleListener(
                executionService, Runnable::run, retryScheduler
        );
        LiveMinuteCandle candle = candle();
        given(executionService.handle(candle)).willReturn(true);

        listener.onLiveMinuteCandle(candle);

        then(retryScheduler).should().schedule(any(Runnable.class), any(Instant.class));
    }

    private LiveMinuteCandle candle() {
        return new LiveMinuteCandle(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "KRW-BTC", 60, 1_800_000_000_000L, "100", "100", "100", "100",
                "1", "100", 1_800_000_000_000L, 1L
        );
    }
}
