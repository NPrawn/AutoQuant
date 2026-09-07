package com.ssafy.a107.trading.backtest.engine.performance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class CumulativeReturnCalculatorTest {

    private final CumulativeReturnCalculator calculator =
            new CumulativeReturnCalculator();

    @DisplayName("초기 자산보다 증가한 비율을 누적 수익률로 계산한다")
    @Test
    void calculatePositiveReturn() {
        BigDecimal returnRate = calculator.calculate(
                BigDecimal.valueOf(1_000_000),
                BigDecimal.valueOf(1_100_000)
        );

        assertThat(returnRate).isEqualByComparingTo("0.10000000");
        assertThat(returnRate.scale()).isEqualTo(8);
    }

    @DisplayName("초기 자산보다 감소한 비율을 음수 누적 수익률로 계산한다")
    @Test
    void calculateNegativeReturn() {
        BigDecimal returnRate = calculator.calculate(
                BigDecimal.valueOf(1_000_000),
                BigDecimal.valueOf(800_000)
        );

        assertThat(returnRate).isEqualByComparingTo("-0.20000000");
    }

    @DisplayName("현재 자산이 초기 자산과 같으면 누적 수익률은 0이다")
    @Test
    void returnZeroWhenAssetValueIsUnchanged() {
        BigDecimal returnRate = calculator.calculate(
                BigDecimal.valueOf(1_000_000),
                BigDecimal.valueOf(1_000_000)
        );

        assertThat(returnRate).isZero();
    }

    @DisplayName("현재 자산이 0이면 누적 수익률은 마이너스 1이다")
    @Test
    void returnNegativeOneForTotalLoss() {
        BigDecimal returnRate = calculator.calculate(
                BigDecimal.valueOf(1_000_000),
                BigDecimal.ZERO
        );

        assertThat(returnRate).isEqualByComparingTo("-1.00000000");
    }

    @DisplayName("초기 자산은 0보다 커야 한다")
    @Test
    void rejectZeroInitialAssetValue() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> calculator.calculate(
                        BigDecimal.ZERO,
                        BigDecimal.valueOf(1_000_000)
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
