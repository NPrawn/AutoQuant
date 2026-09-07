package com.ssafy.a107.trading.backtest.engine.performance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class VolatilityCalculatorTest {

    private final VolatilityCalculator calculator =
            new VolatilityCalculator();

    @DisplayName("기간 수익률의 모집단 표준편차를 계산한다")
    @Test
    void calculatePopulationStandardDeviation() {
        List<BigDecimal> periodicReturns = List.of(
                new BigDecimal("-0.1"),
                BigDecimal.ZERO,
                new BigDecimal("0.1")
        );

        BigDecimal volatility = calculator.calculateAnnualized(
                periodicReturns,
                1
        );

        assertThat(volatility).isEqualByComparingTo("0.08164966");
    }

    @DisplayName("기간 수익률의 변동성을 연환산한다")
    @Test
    void calculateAnnualizedVolatility() {
        List<BigDecimal> periodicReturns = List.of(
                new BigDecimal("-0.1"),
                BigDecimal.ZERO,
                new BigDecimal("0.1")
        );

        BigDecimal volatility = calculator.calculateAnnualized(
                periodicReturns,
                4
        );

        assertThat(volatility).isEqualByComparingTo("0.16329932");
    }

    @DisplayName("모든 기간 수익률이 같으면 변동성은 0이다")
    @Test
    void returnZeroWhenAllReturnsAreEqual() {
        List<BigDecimal> periodicReturns = List.of(
                new BigDecimal("0.05"),
                new BigDecimal("0.05")
        );

        BigDecimal volatility = calculator.calculateAnnualized(
                periodicReturns,
                365
        );

        assertThat(volatility).isEqualByComparingTo("0.00000000");
    }

    @DisplayName("기간 수익률이 두 개보다 적으면 변동성을 계산할 수 없다")
    @Test
    void rejectInsufficientReturns() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> calculator.calculateAnnualized(
                        List.of(BigDecimal.ZERO),
                        365
                ));
    }

    @DisplayName("연간 기간 수는 0보다 커야 한다")
    @Test
    void rejectNonPositivePeriodsPerYear() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> calculator.calculateAnnualized(
                        List.of(BigDecimal.ZERO, BigDecimal.ONE),
                        0
                ));
    }

    @DisplayName("기간 수익률은 -1보다 작을 수 없다")
    @Test
    void rejectReturnLessThanNegativeOne() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> calculator.calculateAnnualized(
                        List.of(new BigDecimal("-1.01"), BigDecimal.ZERO),
                        365
                ));
    }

    @DisplayName("기간 수익률에 null이 포함될 수 없다")
    @Test
    void rejectNullReturn() {
        List<BigDecimal> periodicReturns = new ArrayList<>();
        periodicReturns.add(BigDecimal.ZERO);
        periodicReturns.add(null);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> calculator.calculateAnnualized(
                        periodicReturns,
                        365
                ));
    }
}
