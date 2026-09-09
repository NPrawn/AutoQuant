package com.ssafy.a107.trading.backtest.messaging;

import com.ssafy.a107.trading.backtest.service.BacktestJobExecutionService;
import com.ssafy.a107.trading.backtest.service.BacktestJobFailureService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.ImmediateRequeueAmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
        when(executionService.execute(message, false)).thenReturn(true);

        listener(executionService, failureService).consume(message, rawMessage(false));

        verify(executionService).execute(message, false);
        verify(failureService, never()).fail(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @DisplayName("재전달된 작업은 redelivery 표식과 함께 실행 서비스에 전달한다")
    @Test
    void passRedeliveryFlagToExecutionService() {
        BacktestJobExecutionService executionService = mock(
                BacktestJobExecutionService.class
        );
        BacktestJobFailureService failureService = mock(
                BacktestJobFailureService.class
        );
        BacktestJobMessage message = message();
        when(executionService.execute(message, true)).thenReturn(true);

        listener(executionService, failureService).consume(message, rawMessage(true));

        verify(executionService).execute(message, true);
    }

    @DisplayName("작업 실행 예외는 실패 상태 저장 후 broker DLQ로 거부한다")
    @Test
    void rejectBacktestJobWhenExecutionFails() {
        BacktestJobExecutionService executionService = mock(
                BacktestJobExecutionService.class
        );
        BacktestJobFailureService failureService = mock(
                BacktestJobFailureService.class
        );
        BacktestJobMessage message = message();
        IllegalStateException exception = new IllegalStateException("실행 실패");
        doThrow(exception).when(executionService).execute(message, false);
        when(failureService.fail(BACKTEST_ID, exception)).thenReturn(true);

        assertThatThrownBy(() -> listener(executionService, failureService)
                .consume(message, rawMessage(false)))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasCause(exception);

        then(failureService).should().fail(BACKTEST_ID, exception);
    }

    @DisplayName("이미 종료된 작업의 실행 예외는 broker DLQ로 중복 이동하지 않는다")
    @Test
    void doNotRejectWhenFailureStateWasNotChanged() {
        BacktestJobExecutionService executionService = mock(
                BacktestJobExecutionService.class
        );
        BacktestJobFailureService failureService = mock(
                BacktestJobFailureService.class
        );
        BacktestJobMessage message = message();
        IllegalStateException exception = new IllegalStateException("실행 실패");
        doThrow(exception).when(executionService).execute(message, false);
        when(failureService.fail(BACKTEST_ID, exception)).thenReturn(false);

        listener(executionService, failureService).consume(message, rawMessage(false));
    }

    @DisplayName("실패 상태 저장에 실패하면 작업을 재큐잉한다")
    @Test
    void requeueBacktestJobWhenFailureStateCannotBePersisted() {
        BacktestJobExecutionService executionService = mock(
                BacktestJobExecutionService.class
        );
        BacktestJobFailureService failureService = mock(
                BacktestJobFailureService.class
        );
        BacktestJobMessage message = message();
        IllegalStateException executionException = new IllegalStateException("execution failed");
        IllegalStateException persistenceException = new IllegalStateException("persistence failed");
        doThrow(executionException).when(executionService).execute(message, false);
        doThrow(persistenceException).when(failureService).fail(
                BACKTEST_ID,
                executionException
        );

        assertThatThrownBy(() -> listener(executionService, failureService)
                .consume(message, rawMessage(false)))
                .isInstanceOf(ImmediateRequeueAmqpException.class)
                .hasCause(persistenceException);

        then(failureService).should().fail(BACKTEST_ID, executionException);
    }

    private BacktestJobListener listener(
            BacktestJobExecutionService executionService,
            BacktestJobFailureService failureService
    ) {
        return new BacktestJobListener(executionService, failureService);
    }

    private Message rawMessage(boolean redelivered) {
        MessageProperties properties = new MessageProperties();
        properties.setRedelivered(redelivered);
        return new Message(new byte[0], properties);
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
