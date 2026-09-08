package com.ssafy.a107.trading.backtest.engine.performance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class DrawdownRateCalculatorTest {

    private final DrawdownRateCalculator calculator =
            new DrawdownRateCalculator();

    @DisplayName("현재 자산의 고점 대비 낙폭을 계산한다")
    @Test
    void calculateDrawdownRate() {
        BigDecimal drawdownRate = calculator.calculate(
                BigDecimal.valueOf(1_000_000),
                BigDecimal.valueOf(800_000)
        );

        assertThat(drawdownRate).isEqualByComparingTo("0.20000000");
        assertThat(drawdownRate.scale()).isEqualTo(8);
    }

    @DisplayName("현재 자산이 고점과 같으면 낙폭은 0이다")
    @Test
    void returnZeroAtPeak() {
        BigDecimal drawdownRate = calculator.calculate(
                BigDecimal.valueOf(1_000_000),
                BigDecimal.valueOf(1_000_000)
        );

        assertThat(drawdownRate).isZero();
        assertThat(drawdownRate.scale()).isEqualTo(8);
    }

    @DisplayName("현재 자산이 기존 고점을 넘으면 낙폭은 0이다")
    @Test
    void returnZeroAbovePeak() {
        BigDecimal drawdownRate = calculator.calculate(
                BigDecimal.valueOf(1_000_000),
                BigDecimal.valueOf(1_100_000)
        );

        assertThat(drawdownRate).isZero();
    }

    @DisplayName("현재 자산이 0이면 낙폭은 1이다")
    @Test
    void returnOneForTotalLoss() {
        BigDecimal drawdownRate = calculator.calculate(
                BigDecimal.valueOf(1_000_000),
                BigDecimal.ZERO
        );

        assertThat(drawdownRate).isEqualByComparingTo("1.00000000");
    }

    @DisplayName("고점 자산은 0보다 커야 한다")
    @Test
    void rejectZeroPeakAssetValue() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> calculator.calculate(
                        BigDecimal.ZERO,
                        BigDecimal.ZERO
                ));
    }

    @DisplayName("현재 자산은 음수일 수 없다")
    @Test
    void rejectNegativeCurrentAssetValue() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> calculator.calculate(
                        BigDecimal.valueOf(1_000_000),
                        BigDecimal.valueOf(-1)
                ));
    }
}
