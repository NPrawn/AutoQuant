package com.ssafy.a107.trading.backtest.engine.performance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class SharpeRatioCalculatorTest {

    private final SharpeRatioCalculator calculator =
            new SharpeRatioCalculator();

    @DisplayName("기간 수익률로 연환산 샤프 지수를 계산한다")
    @Test
    void calculateAnnualizedSharpeRatio() {
        List<BigDecimal> periodicReturns = List.of(
                new BigDecimal("0.01"),
                new BigDecimal("0.02"),
                new BigDecimal("0.03")
        );

        BigDecimal sharpeRatio = calculator.calculateAnnualized(
                periodicReturns,
                4
        );

        assertThat(sharpeRatio).isEqualByComparingTo("4.89897949");
    }

    @DisplayName("평균 기간 수익률이 음수이면 샤프 지수도 음수이다")
    @Test
    void calculateNegativeSharpeRatio() {
        List<BigDecimal> periodicReturns = List.of(
                new BigDecimal("-0.03"),
                new BigDecimal("-0.02"),
                new BigDecimal("-0.01")
        );

        BigDecimal sharpeRatio = calculator.calculateAnnualized(
                periodicReturns,
                4
        );

        assertThat(sharpeRatio).isEqualByComparingTo("-4.89897949");
    }

    @DisplayName("평균 기간 수익률이 0이면 샤프 지수는 0이다")
    @Test
    void returnZeroWhenMeanReturnIsZero() {
        List<BigDecimal> periodicReturns = List.of(
                new BigDecimal("-0.1"),
                BigDecimal.ZERO,
                new BigDecimal("0.1")
        );

        BigDecimal sharpeRatio = calculator.calculateAnnualized(
                periodicReturns,
                365
        );

        assertThat(sharpeRatio).isEqualByComparingTo("0.00000000");
    }

    @DisplayName("변동성이 0이면 샤프 지수를 계산할 수 없다")
    @Test
    void rejectZeroVolatility() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> calculator.calculateAnnualized(
                        List.of(
                                new BigDecimal("0.05"),
                                new BigDecimal("0.05")
                        ),
                        365
                ));
    }

    @DisplayName("기간 수익률이 두 개보다 적으면 샤프 지수를 계산할 수 없다")
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
