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

class MaximumDrawdownCalculatorTest {

    private final MaximumDrawdownCalculator calculator =
            new MaximumDrawdownCalculator();

    @DisplayName("자산 평가 스냅샷 중 가장 큰 낙폭을 MDD로 반환한다")
    @Test
    void calculateMaximumDrawdown() {
        List<EquitySnapshot> snapshots = List.of(
                snapshot("0.00000000", 0),
                snapshot("0.12000000", 1),
                snapshot("0.08000000", 2),
                snapshot("0.25000000", 3)
        );

        BigDecimal maximumDrawdown = calculator.calculate(snapshots);

        assertThat(maximumDrawdown).isEqualByComparingTo("0.25000000");
    }

    @DisplayName("모든 평가 시점에 낙폭이 없으면 MDD는 0이다")
    @Test
    void returnZeroWhenThereIsNoDrawdown() {
        List<EquitySnapshot> snapshots = List.of(
                snapshot("0.00000000", 0),
                snapshot("0.00000000", 1)
        );

        BigDecimal maximumDrawdown = calculator.calculate(snapshots);

        assertThat(maximumDrawdown).isZero();
    }

    @DisplayName("자산 평가 스냅샷이 없으면 MDD를 계산할 수 없다")
    @Test
    void rejectEmptySnapshots() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> calculator.calculate(List.of()));
    }

    @DisplayName("자산 평가 스냅샷에 null 요소가 포함될 수 없다")
    @Test
    void rejectNullSnapshot() {
        List<EquitySnapshot> snapshots = new ArrayList<>();
        snapshots.add(snapshot("0.00000000", 0));
        snapshots.add(null);

        assertThatNullPointerException()
                .isThrownBy(() -> calculator.calculate(snapshots));
    }

    private EquitySnapshot snapshot(String drawdownRate, long hourOffset) {
        return new EquitySnapshot(
                Instant.parse("2026-01-02T00:00:00Z").plusSeconds(hourOffset * 3_600),
                BigDecimal.valueOf(1_000_000),
                BigDecimal.valueOf(100_000),
                BigDecimal.ZERO,
                new BigDecimal(drawdownRate)
        );
    }
}
