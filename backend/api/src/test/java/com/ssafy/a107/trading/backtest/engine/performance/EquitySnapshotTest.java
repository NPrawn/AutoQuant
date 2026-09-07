package com.ssafy.a107.trading.backtest.engine.performance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class EquitySnapshotTest {

    private static final Instant VALUATION_AT = Instant.parse("2026-01-02T01:00:00Z");

    @DisplayName("유효한 백테스트 자산 평가 스냅샷을 생성한다")
    @Test
    void createEquitySnapshot() {
        EquitySnapshot snapshot = new EquitySnapshot(
                VALUATION_AT,
                BigDecimal.valueOf(1_100_000),
                BigDecimal.valueOf(100_000),
                BigDecimal.valueOf(0.1),
                BigDecimal.valueOf(0.05)
        );

        assertThat(snapshot.valuationAt()).isEqualTo(VALUATION_AT);
        assertThat(snapshot.totalAssetValue()).isEqualByComparingTo("1100000");
        assertThat(snapshot.cashBalance()).isEqualByComparingTo("100000");
        assertThat(snapshot.cumulativeReturn()).isEqualByComparingTo("0.1");
        assertThat(snapshot.drawdownRate()).isEqualByComparingTo("0.05");
    }

    @DisplayName("전액 손실 상태의 자산 평가 스냅샷을 생성할 수 있다")
    @Test
    void createTotalLossSnapshot() {
        EquitySnapshot snapshot = new EquitySnapshot(
                VALUATION_AT,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ONE.negate(),
                BigDecimal.ONE
        );

        assertThat(snapshot.totalAssetValue()).isZero();
        assertThat(snapshot.cumulativeReturn()).isEqualByComparingTo("-1");
        assertThat(snapshot.drawdownRate()).isEqualByComparingTo("1");
    }

    @DisplayName("총 평가자산은 음수일 수 없다")
    @Test
    void rejectNegativeTotalAssetValue() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> snapshot(
                        BigDecimal.valueOf(-1),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO
                ));
    }

    @DisplayName("현금 잔액은 음수일 수 없다")
    @Test
    void rejectNegativeCashBalance() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> snapshot(
                        BigDecimal.valueOf(1_000_000),
                        BigDecimal.valueOf(-1),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO
                ));
    }

    @DisplayName("현금 잔액은 총 평가자산을 초과할 수 없다")
    @Test
    void rejectCashBalanceExceedingTotalAssetValue() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> snapshot(
                        BigDecimal.valueOf(1_000_000),
                        BigDecimal.valueOf(1_000_001),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO
                ));
    }

    @DisplayName("누적 수익률은 마이너스 1보다 작을 수 없다")
    @Test
    void rejectCumulativeReturnBelowNegativeOne() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> snapshot(
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        new BigDecimal("-1.00000001"),
                        BigDecimal.ONE
                ));
    }

    @DisplayName("낙폭은 음수일 수 없다")
    @Test
    void rejectNegativeDrawdownRate() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> snapshot(
                        BigDecimal.valueOf(1_000_000),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.valueOf(-0.1)
                ));
    }

    @DisplayName("낙폭은 1보다 클 수 없다")
    @Test
    void rejectDrawdownRateAboveOne() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> snapshot(
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ONE.negate(),
                        new BigDecimal("1.00000001")
                ));
    }

    private EquitySnapshot snapshot(
            BigDecimal totalAssetValue,
            BigDecimal cashBalance,
            BigDecimal cumulativeReturn,
            BigDecimal drawdownRate
    ) {
        return new EquitySnapshot(
                VALUATION_AT,
                totalAssetValue,
                cashBalance,
                cumulativeReturn,
                drawdownRate
        );
    }
}
