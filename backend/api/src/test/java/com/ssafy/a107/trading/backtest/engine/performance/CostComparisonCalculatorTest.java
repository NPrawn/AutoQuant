package com.ssafy.a107.trading.backtest.engine.performance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class CostComparisonCalculatorTest {

    private final CostComparisonCalculator calculator =
            new CostComparisonCalculator(
                    new CumulativeReturnCalculator()
            );

    @DisplayName("거래 비용을 복원해 비용 적용 전후 성과를 계산한다")
    @Test
    void calculateCostComparison() {
        CostComparison comparison = calculator.calculate(
                new BigDecimal("1000000"),
                new BigDecimal("1100000"),
                new TradeStatistics(
                        4,
                        new BigDecimal("2000"),
                        new BigDecimal("3000")
                )
        );

        assertThat(comparison.finalAssetValueBeforeCosts())
                .isEqualByComparingTo("1105000.00000000");
        assertThat(comparison.finalAssetValueAfterCosts())
                .isEqualByComparingTo("1100000.00000000");
        assertThat(comparison.returnBeforeCosts())
                .isEqualByComparingTo("0.10500000");
        assertThat(comparison.returnAfterCosts())
                .isEqualByComparingTo("0.10000000");
        assertThat(comparison.costImpactRate())
                .isEqualByComparingTo("0.00500000");
    }

    @DisplayName("거래 비용이 없으면 비용 적용 전후 성과가 같다")
    @Test
    void returnSamePerformanceWhenCostsAreZero() {
        CostComparison comparison = calculator.calculate(
                new BigDecimal("1000000"),
                new BigDecimal("900000"),
                new TradeStatistics(
                        0,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO
                )
        );

        assertThat(comparison.returnBeforeCosts())
                .isEqualByComparingTo("-0.10000000");
        assertThat(comparison.returnAfterCosts())
                .isEqualByComparingTo("-0.10000000");
        assertThat(comparison.costImpactRate()).isZero();
    }

    @DisplayName("초기자본은 0보다 커야 한다")
    @Test
    void rejectNonPositiveInitialCapital() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> calculator.calculate(
                        BigDecimal.ZERO,
                        BigDecimal.ONE,
                        new TradeStatistics(
                                0,
                                BigDecimal.ZERO,
                                BigDecimal.ZERO
                        )
                ));
    }

    @DisplayName("비용 적용 후 최종자산은 음수일 수 없다")
    @Test
    void rejectNegativeFinalAssetValue() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> calculator.calculate(
                        BigDecimal.ONE,
                        BigDecimal.ONE.negate(),
                        new TradeStatistics(
                                0,
                                BigDecimal.ZERO,
                                BigDecimal.ZERO
                        )
                ));
    }

    @DisplayName("거래 통계는 null일 수 없다")
    @Test
    void rejectNullTradeStatistics() {
        assertThatNullPointerException()
                .isThrownBy(() -> calculator.calculate(
                        BigDecimal.ONE,
                        BigDecimal.ONE,
                        null
                ));
    }
}
