package com.ssafy.a107.trading.backtest.engine.performance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ExcessReturnCalculatorTest {

    private final ExcessReturnCalculator calculator =
            new ExcessReturnCalculator();

    @DisplayName("전략 수익률이 벤치마크보다 높으면 양수 초과수익률을 계산한다")
    @Test
    void calculatePositiveExcessReturn() {
        BigDecimal excessReturn = calculator.calculate(
                new BigDecimal("0.25"),
                new BigDecimal("0.10")
        );

        assertThat(excessReturn).isEqualByComparingTo("0.15000000");
    }

    @DisplayName("전략 수익률이 벤치마크보다 낮으면 음수 초과수익률을 계산한다")
    @Test
    void calculateNegativeExcessReturn() {
        BigDecimal excessReturn = calculator.calculate(
                new BigDecimal("-0.05"),
                new BigDecimal("0.10")
        );

        assertThat(excessReturn).isEqualByComparingTo("-0.15000000");
    }

    @DisplayName("전략 수익률은 null일 수 없다")
    @Test
    void rejectNullStrategyReturn() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> calculator.calculate(
                        null,
                        BigDecimal.ZERO
                ));
    }

    @DisplayName("벤치마크 수익률은 null일 수 없다")
    @Test
    void rejectNullBenchmarkReturn() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> calculator.calculate(
                        BigDecimal.ZERO,
                        null
                ));
    }
}
