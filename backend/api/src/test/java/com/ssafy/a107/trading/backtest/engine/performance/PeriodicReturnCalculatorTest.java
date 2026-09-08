package com.ssafy.a107.trading.backtest.engine.performance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class PeriodicReturnCalculatorTest {

    private static final Instant FIRST_VALUATION_AT = Instant.parse(
            "2026-01-02T00:00:00Z"
    );

    private final PeriodicReturnCalculator calculator =
            new PeriodicReturnCalculator();

    @DisplayName("연속한 자산 평가금액으로 기간 수익률을 계산한다")
    @Test
    void calculatePeriodicReturns() {
        List<EquitySnapshot> snapshots = List.of(
                snapshot("1000000", 0),
                snapshot("1100000", 1),
                snapshot("990000", 2)
        );

        List<BigDecimal> periodicReturns = calculator.calculate(snapshots);

        assertThat(periodicReturns).hasSize(2);
        assertThat(periodicReturns.get(0)).isEqualByComparingTo("0.1");
        assertThat(periodicReturns.get(1)).isEqualByComparingTo("-0.1");
    }

    @DisplayName("자산 평가 스냅샷이 두 개보다 적으면 기간 수익률을 계산할 수 없다")
    @Test
    void rejectInsufficientSnapshots() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> calculator.calculate(List.of(
                        snapshot("1000000", 0)
                )));
    }

    @DisplayName("자산 평가 스냅샷은 시각 오름차순이어야 한다")
    @Test
    void rejectUnorderedSnapshots() {
        List<EquitySnapshot> snapshots = List.of(
                snapshot("1000000", 1),
                snapshot("1100000", 0)
        );

        assertThatIllegalArgumentException()
                .isThrownBy(() -> calculator.calculate(snapshots));
    }

    @DisplayName("이전 평가자산이 0이면 기간 수익률을 계산할 수 없다")
    @Test
    void rejectZeroPreviousAssetValue() {
        List<EquitySnapshot> snapshots = List.of(
                snapshot("0", 0),
                snapshot("1000000", 1)
        );

        assertThatIllegalArgumentException()
                .isThrownBy(() -> calculator.calculate(snapshots));
    }

    @DisplayName("자산 평가 스냅샷에 null 요소가 포함될 수 없다")
    @Test
    void rejectNullSnapshot() {
        List<EquitySnapshot> snapshots = new ArrayList<>();
        snapshots.add(snapshot("1000000", 0));
        snapshots.add(null);

        assertThatNullPointerException()
                .isThrownBy(() -> calculator.calculate(snapshots));
    }

    private EquitySnapshot snapshot(String totalAssetValue, long hourOffset) {
        BigDecimal value = new BigDecimal(totalAssetValue);
        BigDecimal cashBalance = value.signum() == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(100_000);
        BigDecimal cumulativeReturn = value.signum() == 0
                ? BigDecimal.ONE.negate()
                : BigDecimal.ZERO;
        BigDecimal drawdownRate = value.signum() == 0
                ? BigDecimal.ONE
                : BigDecimal.ZERO;

        return new EquitySnapshot(
                FIRST_VALUATION_AT.plusSeconds(hourOffset * 3_600),
                value,
                cashBalance,
                cumulativeReturn,
                drawdownRate
        );
    }
}
