package com.ssafy.a107.trading.backtest.engine.execution;

import com.ssafy.a107.trading.backtest.domain.BacktestRun;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BacktestExecutionPolicyResolverTest {

    @DisplayName("실행 설정과 Run 비용 설정으로 가상 체결 정책을 생성한다")
    @Test
    void resolveExecutionPolicy() {
        BacktestExecutionPolicyResolver resolver =
                new BacktestExecutionPolicyResolver(
                        new BacktestExecutionPolicyProperties(
                                BigDecimal.valueOf(5_000),
                                BigDecimal.ONE,
                                new BigDecimal("0.00000001")
                        )
                );
        BacktestRun backtestRun = mock(BacktestRun.class);
        when(backtestRun.getFeeRate()).thenReturn(new BigDecimal("0.0005"));
        when(backtestRun.getSlippageBps()).thenReturn(BigDecimal.valueOf(10));

        VirtualExecutionPolicy policy = resolver.resolve(backtestRun);

        assertThat(policy.feeRate()).isEqualByComparingTo("0.0005");
        assertThat(policy.slippageBps()).isEqualByComparingTo("10");
        assertThat(policy.minimumOrderAmount()).isEqualByComparingTo("5000");
        assertThat(policy.priceTickSize()).isEqualByComparingTo("1");
        assertThat(policy.quantityStepSize()).isEqualByComparingTo("0.00000001");
    }
}
