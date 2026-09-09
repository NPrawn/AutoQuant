package com.ssafy.a107.trading.backtest.messaging;

import com.ssafy.a107.trading.backtest.service.BacktestJobExecutionService;
import com.ssafy.a107.trading.backtest.service.BacktestJobFailureService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BacktestJobListenerTest {

    private static final UUID BACKTEST_ID = UUID.fromString(
            "11111111-1111-1111-1111-111111111111"
    );

    @DisplayName("작업 실행이 성공하면 실패 상태 처리를 호출하지 않는다")
    @Test
    void consumeBacktestJob() {
        BacktestJobExecutionService executionService = mock(
                BacktestJobExecutionService.class
        );
        BacktestJobFailureService failureService = mock(
                BacktestJobFailureService.class
        );
        BacktestJobMessage message = message();
        when(executionService.execute(message)).thenReturn(true);

        listener(executionService, failureService).consume(message);

        verify(executionService).execute(message);
        verify(failureService, never()).fail(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @DisplayName("작업 실행 예외는 실패 상태 저장 후 다시 전파한다")
    @Test
    void failBacktestJobWhenExecutionFails() {
        BacktestJobExecutionService executionService = mock(
                BacktestJobExecutionService.class
        );
        BacktestJobFailureService failureService = mock(
                BacktestJobFailureService.class
        );
        BacktestJobMessage message = message();
        IllegalStateException exception = new IllegalStateException("실행 실패");
        doThrow(exception).when(executionService).execute(message);

        assertThatIllegalStateException()
                .isThrownBy(() -> listener(
                        executionService,
                        failureService
                ).consume(message))
                .isSameAs(exception);

        then(failureService).should().fail(BACKTEST_ID, exception);
    }

    private BacktestJobListener listener(
            BacktestJobExecutionService executionService,
            BacktestJobFailureService failureService
    ) {
        return new BacktestJobListener(executionService, failureService);
    }

    private BacktestJobMessage message() {
        return new BacktestJobMessage(
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                BACKTEST_ID,
                1L,
                "market-v1",
                "engine-v1"
        );
    }
}
