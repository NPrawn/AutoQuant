package com.ssafy.a107.trading.backtest.service;

import com.ssafy.a107.trading.backtest.domain.BacktestRun;
import com.ssafy.a107.trading.backtest.domain.BacktestRunStatus;
import com.ssafy.a107.trading.backtest.repository.BacktestRunRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class BacktestJobFailureServiceTest {

    private static final UUID BACKTEST_ID = UUID.fromString(
            "11111111-1111-1111-1111-111111111111"
    );

    @Mock
    private BacktestRunRepository backtestRunRepository;

    @Mock
    private BacktestRun backtestRun;

    @DisplayName("진행 중인 작업을 예외 원인과 함께 실패 상태로 전이한다")
    @Test
    void failRunningBacktestJob() {
        given(backtestRunRepository.findByBacktestIdForUpdate(BACKTEST_ID))
                .willReturn(Optional.of(backtestRun));
        given(backtestRun.getStatus()).willReturn(BacktestRunStatus.RUNNING);

        boolean failed = service().fail(
                BACKTEST_ID,
                new IllegalStateException("시장 데이터가 부족합니다")
        );

        assertThat(failed).isTrue();
        then(backtestRun).should().fail(
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
        then(backtestRun).should(never()).fail(anyString());
    }

    private BacktestJobFailureService service() {
        return new BacktestJobFailureService(backtestRunRepository);
    }
}
