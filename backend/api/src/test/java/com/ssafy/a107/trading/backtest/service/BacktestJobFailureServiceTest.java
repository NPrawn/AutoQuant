package com.ssafy.a107.trading.backtest.service;

import com.ssafy.a107.trading.backtest.domain.BacktestRun;
import com.ssafy.a107.trading.backtest.domain.BacktestFailureCode;
import com.ssafy.a107.trading.backtest.domain.BacktestRunStatus;
import com.ssafy.a107.strategy.engine.InsufficientCandleDataException;
import com.ssafy.a107.trading.backtest.engine.BacktestExecutionException;
import com.ssafy.a107.member.domain.Member;
import com.ssafy.a107.notification.domain.NotificationType;
import com.ssafy.a107.notification.service.NotificationRequestedEvent;
import com.ssafy.a107.strategy.domain.Strategy;
import com.ssafy.a107.trading.backtest.repository.BacktestRunRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class BacktestJobFailureServiceTest {

    private static final UUID BACKTEST_ID = UUID.fromString(
            "11111111-1111-1111-1111-111111111111"
    );
    private static final UUID MEMBER_ID = UUID.fromString(
            "22222222-2222-2222-2222-222222222222"
    );
    private static final String STRATEGY_NAME = "이평선 골든크로스";

    @Mock
    private BacktestRunRepository backtestRunRepository;

    @Mock
    private BacktestRun backtestRun;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @DisplayName("진행 중인 작업을 예외 원인과 함께 실패 상태로 전이한다")
    @Test
    void failRunningBacktestJob() {
        given(backtestRunRepository.findByBacktestIdForUpdate(BACKTEST_ID))
                .willReturn(Optional.of(backtestRun));
        given(backtestRun.getStatus()).willReturn(BacktestRunStatus.RUNNING);
        givenNotifiableRun();

        boolean failed = service().fail(
                BACKTEST_ID,
                new IllegalStateException("시장 데이터가 부족합니다")
        );

        assertThat(failed).isTrue();
        then(backtestRun).should().fail(
                BacktestFailureCode.ENGINE_ERROR,
                "IllegalStateException: 시장 데이터가 부족합니다"
        );
    }

    @DisplayName("이미 완료된 작업은 다시 실패 상태로 전이하지 않는다")
    @Test
    void ignoreCompletedBacktestJob() {
        given(backtestRunRepository.findByBacktestIdForUpdate(BACKTEST_ID))
                .willReturn(Optional.of(backtestRun));
        given(backtestRun.getStatus()).willReturn(BacktestRunStatus.COMPLETED);

        boolean failed = service().fail(
                BACKTEST_ID,
                new IllegalStateException("실행 실패")
        );

        assertThat(failed).isFalse();
        then(backtestRun).should(never()).fail(any(), anyString());
    }

    @DisplayName("캔들이 부족한 전략 실행 오류는 캔들 부족 코드로 기록한다")
    @Test
    void classifyInsufficientCandleData() {
        given(backtestRunRepository.findByBacktestIdForUpdate(BACKTEST_ID))
                .willReturn(Optional.of(backtestRun));
        given(backtestRun.getStatus()).willReturn(BacktestRunStatus.RUNNING);
        givenNotifiableRun();

        service().fail(
                BACKTEST_ID,
                new InsufficientCandleDataException("Insufficient candle data")
        );

        then(backtestRun).should().fail(
                BacktestFailureCode.INSUFFICIENT_CANDLES,
                "InsufficientCandleDataException: Insufficient candle data"
        );
    }

    @DisplayName("유니버스가 비어 실행할 수 없으면 유니버스 없음 코드로 기록한다")
    @Test
    void classifyEmptyUniverse() {
        given(backtestRunRepository.findByBacktestIdForUpdate(BACKTEST_ID))
                .willReturn(Optional.of(backtestRun));
        given(backtestRun.getStatus()).willReturn(BacktestRunStatus.RUNNING);
        givenNotifiableRun();

        service().fail(
                BACKTEST_ID,
                new BacktestExecutionException(
                        BacktestFailureCode.EMPTY_UNIVERSE,
                        "Backtest universe must not be empty"
                )
        );

        then(backtestRun).should().fail(
                BacktestFailureCode.EMPTY_UNIVERSE,
                "BacktestExecutionException: Backtest universe must not be empty"
        );
    }

    @DisplayName("대회 백테스트는 실패 알림을 내지 않는다. 그 제출은 채점 알림에서 실격으로 알린다")
    @Test
    void skipNotificationForContestRun() {
        given(backtestRunRepository.findByBacktestIdForUpdate(BACKTEST_ID))
                .willReturn(Optional.of(backtestRun));
        given(backtestRun.getStatus()).willReturn(BacktestRunStatus.RUNNING);
        given(backtestRun.isContest()).willReturn(true);

        assertThat(service().fail(BACKTEST_ID, new InsufficientCandleDataException("warmup 20 candles required")))
                .isTrue();

        then(applicationEventPublisher).shouldHaveNoInteractions();
    }

    @DisplayName("실패를 알리는 알림에 다음에 할 수 있는 일을 담고 내부 예외는 담지 않는다")
    @Test
    void publishFailureNotification() {
        given(backtestRunRepository.findByBacktestIdForUpdate(BACKTEST_ID))
                .willReturn(Optional.of(backtestRun));
        given(backtestRun.getStatus()).willReturn(BacktestRunStatus.RUNNING);
        givenNotifiableRun();

        service().fail(BACKTEST_ID, new InsufficientCandleDataException("warmup 20 candles required"));

        ArgumentCaptor<NotificationRequestedEvent> event = ArgumentCaptor.forClass(NotificationRequestedEvent.class);
        then(applicationEventPublisher).should().publishEvent(event.capture());
        assertThat(event.getValue().memberId()).isEqualTo(MEMBER_ID);
        assertThat(event.getValue().notificationType()).isEqualTo(NotificationType.BACKTEST_FAILED);
        assertThat(event.getValue().title()).contains(STRATEGY_NAME);
        assertThat(event.getValue().body()).contains("시작일을 뒤로 미루거나");
        assertThat(event.getValue().body()).doesNotContain("InsufficientCandleDataException");
        assertThat(event.getValue().targetId()).isEqualTo(BACKTEST_ID.toString());
    }

    /* 알림 문구는 전략 이름과 소유자를 읽는다. 그 경로까지 가는 시험에서만 준비한다. */
    private void givenNotifiableRun() {
        Member member = mock(Member.class);
        Strategy strategy = mock(Strategy.class);
        given(member.getMemberId()).willReturn(MEMBER_ID);
        given(strategy.getName()).willReturn(STRATEGY_NAME);
        given(backtestRun.getMember()).willReturn(member);
        given(backtestRun.getStrategy()).willReturn(strategy);
        given(backtestRun.getBacktestId()).willReturn(BACKTEST_ID);
    }

    private BacktestJobFailureService service() {
        return new BacktestJobFailureService(backtestRunRepository, applicationEventPublisher);
    }
}
