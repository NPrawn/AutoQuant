package com.ssafy.a107.trading.backtest.engine.performance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class TradeStatisticsTest {

    @DisplayName("거래 횟수와 총비용으로 거래 통계를 생성한다")
    @Test
    void createTradeStatistics() {
        TradeStatistics statistics = new TradeStatistics(
                3,
                new BigDecimal("1500.00000000"),
                new BigDecimal("3000.00000000")
        );

        assertThat(statistics.tradeCount()).isEqualTo(3);
        assertThat(statistics.totalFee())
                .isEqualByComparingTo("1500.00000000");
        assertThat(statistics.totalSlippage())
                .isEqualByComparingTo("3000.00000000");
    }

    @DisplayName("거래가 없으면 모든 거래 통계 값에 0을 사용할 수 있다")
    @Test
    void allowZeroStatistics() {
        TradeStatistics statistics = new TradeStatistics(
                0,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );

        assertThat(statistics.tradeCount()).isZero();
        assertThat(statistics.totalFee()).isZero();
        assertThat(statistics.totalSlippage()).isZero();
    }

    @DisplayName("거래 횟수는 음수일 수 없다")
    @Test
    void rejectNegativeTradeCount() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TradeStatistics(
                        -1,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO
                ));
    }

    @DisplayName("총수수료는 음수일 수 없다")
    @Test
    void rejectNegativeTotalFee() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TradeStatistics(
                        1,
                        BigDecimal.ONE.negate(),
                        BigDecimal.ZERO
                ));
    }

    @DisplayName("총수수료는 null일 수 없다")
    @Test
    void rejectNullTotalFee() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TradeStatistics(
                        1,
                        null,
                        BigDecimal.ZERO
                ));
    }

    @DisplayName("총슬리피지는 음수일 수 없다")
    @Test
    void rejectNegativeTotalSlippage() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TradeStatistics(
                        1,
                        BigDecimal.ZERO,
                        BigDecimal.ONE.negate()
                ));
    }

    @DisplayName("총슬리피지는 null일 수 없다")
    @Test
    void rejectNullTotalSlippage() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TradeStatistics(
                        1,
                        BigDecimal.ZERO,
                        null
                ));
    }
}
