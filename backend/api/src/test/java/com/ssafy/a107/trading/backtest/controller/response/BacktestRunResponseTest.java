package com.ssafy.a107.trading.backtest.controller.response;

import com.ssafy.a107.trading.backtest.domain.BacktestFailureCode;
import com.ssafy.a107.trading.backtest.domain.BacktestRun;
import com.ssafy.a107.strategy.domain.Strategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class BacktestRunResponseTest {

    @DisplayName("백테스트 실행 응답은 내부 실패 원인을 포함하지 않는다")
    @Test
    void doesNotExposeFailureReason() {
        assertThat(Arrays.stream(BacktestRunResponse.class.getRecordComponents())
                .map(component -> component.getName()))
                .contains("failureCode")
                .doesNotContain("failureReason");
    }

    @DisplayName("실패한 백테스트 실행은 실패 분류 코드만 응답에 포함한다")
    @Test
    void exposeFailureCodeWithoutFailureReason() {
        BacktestRun backtestRun = mock(BacktestRun.class);
        Strategy strategy = mock(Strategy.class);
        given(backtestRun.getStrategy()).willReturn(strategy);
        given(strategy.getStrategyId()).willReturn(1L);
        given(backtestRun.getFailureCode()).willReturn(BacktestFailureCode.INSUFFICIENT_CANDLES);

        BacktestRunResponse response = BacktestRunResponse.from(backtestRun);

        assertThat(response.failureCode()).isEqualTo(BacktestFailureCode.INSUFFICIENT_CANDLES);
    }
}
