package com.ssafy.a107.trading.simulation.service;

import com.ssafy.a107.realtime.domain.LiveMinuteCandle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

class SimulationLiveCandleListenerTest {

    @DisplayName("실시간 Candle 수신은 실행 작업을 별도 Executor에 위임한다")
    @Test
    void delegateLiveCandleToExecutor() {
        SimulationCandleExecutionService executionService = mock(SimulationCandleExecutionService.class);
        AtomicReference<Runnable> submittedTask = new AtomicReference<>();
        TaskExecutor taskExecutor = submittedTask::set;
        SimulationLiveCandleListener listener = new SimulationLiveCandleListener(executionService, taskExecutor);
        LiveMinuteCandle candle = candle();

        listener.onLiveMinuteCandle(candle);

        then(executionService).shouldHaveNoInteractions();
        submittedTask.get().run();
        then(executionService).should().handle(candle);
    }

    private LiveMinuteCandle candle() {
        return new LiveMinuteCandle(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "KRW-BTC", 60, 1_800_000_000_000L, "100", "100", "100", "100",
                "1", "100", 1_800_000_000_000L, 1L
        );
    }
}
