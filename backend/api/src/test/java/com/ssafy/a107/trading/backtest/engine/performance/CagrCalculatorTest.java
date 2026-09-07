package com.ssafy.a107.trading.backtest.engine.performance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CagrCalculatorTest {

    private static final Instant PERIOD_START = Instant.parse(
            "2025-01-01T00:00:00Z"
    );

    private final CagrCalculator calculator = new CagrCalculator();

    @DisplayName("1년 동안의 자산 증가율로 CAGR을 계산한다")
    @Test
    void calculateOneYearCagr() {
        BigDecimal cagr = calculator.calculate(
                new BigDecimal("100"),
                new BigDecimal("121"),
                PERIOD_START,
                PERIOD_START.plus(365, ChronoUnit.DAYS)
        );

        assertThat(cagr).isEqualByComparingTo("0.21000000");
    }

    @DisplayName("1년보다 짧은 기간의 자산 증가율을 연환산한다")
    @Test
    void annualizeShorterPeriodReturn() {
        BigDecimal cagr = calculator.calculate(
                new BigDecimal("100"),
                new BigDecimal("121"),
                PERIOD_START,
                PERIOD_START.plusSeconds(365L * 24 * 60 * 60 / 2)
        );

        assertThat(cagr).isEqualByComparingTo("0.46410000");
    }

    @DisplayName("최종 자산이 감소하면 음수 CAGR을 반환한다")
    @Test
    void calculateNegativeCagr() {
        BigDecimal cagr = calculator.calculate(
                new BigDecimal("100"),
                new BigDecimal("81"),
                PERIOD_START,
                PERIOD_START.plus(365, ChronoUnit.DAYS)
        );

        assertThat(cagr).isEqualByComparingTo("-0.19000000");
    }

    @DisplayName("최종 자산이 0이면 CAGR은 -1이다")
    @Test
    void returnNegativeOneWhenFinalAssetIsZero() {
        BigDecimal cagr = calculator.calculate(
                new BigDecimal("100"),
                BigDecimal.ZERO,
                PERIOD_START,
                PERIOD_START.plus(365, ChronoUnit.DAYS)
        );

        assertThat(cagr).isEqualByComparingTo("-1.00000000");
    }

    @DisplayName("초기 자산은 0보다 커야 한다")
    @Test
    void rejectNonPositiveInitialAssetValue() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> calculator.calculate(
                        BigDecimal.ZERO,
                        new BigDecimal("100"),
                        PERIOD_START,
                        PERIOD_START.plus(365, ChronoUnit.DAYS)
                ));
    }

    @DisplayName("최종 자산은 음수일 수 없다")
    @Test
    void rejectNegativeFinalAssetValue() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> calculator.calculate(
                        new BigDecimal("100"),
                        BigDecimal.ONE.negate(),
                        PERIOD_START,
                        PERIOD_START.plus(365, ChronoUnit.DAYS)
                ));
    }

    @DisplayName("평가 종료 시각은 시작 시각보다 뒤여야 한다")
    @Test
    void rejectNonPositivePeriod() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> calculator.calculate(
                        new BigDecimal("100"),
                        new BigDecimal("110"),
                        PERIOD_START,
                        PERIOD_START
                ));
    }

    @DisplayName("평가 기간 시각은 null일 수 없다")
    @Test
    void rejectNullPeriod() {
        assertThatNullPointerException()
                .isThrownBy(() -> calculator.calculate(
                        new BigDecimal("100"),
                        new BigDecimal("110"),
                        null,
                        PERIOD_START
                ));
    }

    @DisplayName("연환산 결과가 지원 범위를 초과하면 계산을 거절한다")
    @Test
    void rejectUnsupportedAnnualizedGrowth() {
        assertThatThrownBy(() -> calculator.calculate(
                        BigDecimal.ONE,
                        BigDecimal.TEN,
                        PERIOD_START,
                        PERIOD_START.plusSeconds(1)
                ))
                .isInstanceOf(ArithmeticException.class);
    }
}
