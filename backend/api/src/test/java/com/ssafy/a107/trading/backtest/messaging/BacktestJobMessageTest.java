package com.ssafy.a107.trading.backtest.messaging;

import com.ssafy.a107.strategy.domain.Strategy;
import com.ssafy.a107.trading.backtest.domain.BacktestRun;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class BacktestJobMessageTest {

    private static final UUID JOB_ID = UUID.fromString("2fd8cc72-a39b-4fc5-8b4c-cf83ea734dc3");
    private static final UUID BACKTEST_ID = UUID.fromString("e4b4e9e0-8d17-4a47-98d4-4d9f06d97b2b");

    @Test
    @DisplayName("백테스트 실행 설정에서 Worker Job 메시지를 생성한다")
    void createFromBacktestRun() {
        BacktestRun backtestRun = mock(BacktestRun.class);
        Strategy strategy = mock(Strategy.class);
        given(backtestRun.getBacktestId()).willReturn(BACKTEST_ID);
        given(backtestRun.getStrategy()).willReturn(strategy);
        given(strategy.getStrategyId()).willReturn(1L);
        given(backtestRun.getMarketDataVersion()).willReturn("market-v1");
        given(backtestRun.getEngineVersion()).willReturn("engine-v1");

        BacktestJobMessage message = BacktestJobMessage.from(JOB_ID, backtestRun);

        assertThat(message).isEqualTo(new BacktestJobMessage(
                JOB_ID,
                BACKTEST_ID,
                1L,
                "market-v1",
                "engine-v1"
        ));
    }
}
