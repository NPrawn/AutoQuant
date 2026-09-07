package com.ssafy.a107.trading.backtest.engine.performance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class BenchmarkReturnCalculatorTest {

    private final BenchmarkReturnCalculator calculator =
            new BenchmarkReturnCalculator(
                    new CumulativeReturnCalculator()
            );

    @DisplayName("벤치마크 시작가와 종료가로 상승 수익률을 계산한다")
    @Test
    void calculatePositiveBenchmarkReturn() {
        BigDecimal benchmarkReturn = calculator.calculate(
                new BigDecimal("100000000"),
                new BigDecimal("125000000")
        );

        assertThat(benchmarkReturn).isEqualByComparingTo("0.25000000");
    }

    @DisplayName("벤치마크 가격이 하락하면 음수 수익률을 계산한다")
    @Test
    void calculateNegativeBenchmarkReturn() {
        BigDecimal benchmarkReturn = calculator.calculate(
                new BigDecimal("100000000"),
                new BigDecimal("80000000")
        );

        assertThat(benchmarkReturn).isEqualByComparingTo("-0.20000000");
    }

    @DisplayName("벤치마크 시작가는 0보다 커야 한다")
    @Test
    void rejectNonPositiveInitialBenchmarkPrice() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> calculator.calculate(
                        BigDecimal.ZERO,
                        BigDecimal.ONE
                ));
    }

    @DisplayName("벤치마크 종료가는 0보다 커야 한다")
    @Test
    void rejectNonPositiveFinalBenchmarkPrice() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> calculator.calculate(
                        BigDecimal.ONE,
                        BigDecimal.ZERO
                ));
    }
}
