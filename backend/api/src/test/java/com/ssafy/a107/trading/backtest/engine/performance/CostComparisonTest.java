package com.ssafy.a107.trading.backtest.engine.performance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class CostComparisonTest {

    @DisplayName("비용 적용 전후 자산과 수익률로 비용 비교 결과를 생성한다")
    @Test
    void createCostComparison() {
        CostComparison comparison = comparison(
                new BigDecimal("1015000"),
                new BigDecimal("1010000"),
                new BigDecimal("0.015"),
                new BigDecimal("0.01"),
                new BigDecimal("0.005")
        );

        assertThat(comparison.totalFee()).isEqualByComparingTo("2000");
        assertThat(comparison.totalSlippage()).isEqualByComparingTo("3000");
    }

    @DisplayName("비용 적용 전 자산은 적용 후 자산보다 작을 수 없다")
    @Test
    void rejectBeforeCostsValueLessThanAfterCostsValue() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> comparison(
                        new BigDecimal("1000000"),
                        new BigDecimal("1010000"),
                        BigDecimal.ZERO,
                        new BigDecimal("0.01"),
                        BigDecimal.ZERO
                ));
    }

    @DisplayName("거래 비용은 음수일 수 없다")
    @Test
    void rejectNegativeTradeCost() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new CostComparison(
                        BigDecimal.ONE,
                        BigDecimal.ONE,
                        BigDecimal.ONE.negate(),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO
                ));
    }

    @DisplayName("비용 적용 전후 수익률은 null일 수 없다")
    @Test
    void rejectNullReturn() {
        assertThatNullPointerException()
                .isThrownBy(() -> comparison(
                        BigDecimal.ONE,
                        BigDecimal.ONE,
                        null,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO
                ));
    }

    private CostComparison comparison(
            BigDecimal beforeCosts,
            BigDecimal afterCosts,
            BigDecimal returnBeforeCosts,
            BigDecimal returnAfterCosts,
            BigDecimal costImpactRate
    ) {
        return new CostComparison(
                beforeCosts,
                afterCosts,
                new BigDecimal("2000"),
                new BigDecimal("3000"),
                returnBeforeCosts,
                returnAfterCosts,
                costImpactRate
        );
    }
}
