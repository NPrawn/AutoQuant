package com.ssafy.a107.trading.backtest.engine.performance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class EquityTrackerTest {

    private static final Instant FIRST_VALUATION_AT = Instant.parse(
            "2026-01-02T01:00:00Z"
    );

    @DisplayName("초기 자산과 같은 평가금액은 수익률과 낙폭이 0이다")
    @Test
    void recordInitialAssetValue() {
        EquityTracker tracker = tracker();

        EquitySnapshot snapshot = tracker.record(
                FIRST_VALUATION_AT,
                BigDecimal.valueOf(1_000_000),
                BigDecimal.valueOf(100_000)
        );

        assertThat(snapshot.cumulativeReturn()).isZero();
        assertThat(snapshot.drawdownRate()).isZero();
    }

    @DisplayName("신고점 이후 하락한 평가금액의 누적 수익률과 낙폭을 계산한다")
    @Test
    void recordDrawdownAfterPeak() {
        EquityTracker tracker = tracker();
        tracker.record(
                FIRST_VALUATION_AT,
                BigDecimal.valueOf(1_200_000),
                BigDecimal.valueOf(100_000)
        );

        EquitySnapshot snapshot = tracker.record(
                FIRST_VALUATION_AT.plusSeconds(3_600),
                BigDecimal.valueOf(900_000),
                BigDecimal.valueOf(100_000)
        );

        assertThat(snapshot.cumulativeReturn()).isEqualByComparingTo("-0.10000000");
        assertThat(snapshot.drawdownRate()).isEqualByComparingTo("0.25000000");
    }

    @DisplayName("이전 고점 아래로 회복하면 기존 고점을 기준으로 낙폭을 계산한다")
    @Test
    void recordRecoveryBelowPeak() {
        EquityTracker tracker = tracker();
        tracker.record(
                FIRST_VALUATION_AT,
                BigDecimal.valueOf(1_200_000),
                BigDecimal.valueOf(100_000)
        );
        tracker.record(
                FIRST_VALUATION_AT.plusSeconds(3_600),
                BigDecimal.valueOf(900_000),
                BigDecimal.valueOf(100_000)
        );

        EquitySnapshot snapshot = tracker.record(
                FIRST_VALUATION_AT.plusSeconds(7_200),
                BigDecimal.valueOf(1_100_000),
                BigDecimal.valueOf(100_000)
        );

        assertThat(snapshot.drawdownRate()).isEqualByComparingTo("0.08333333");
    }

    @DisplayName("새로운 고점을 기록하면 이후 낙폭 기준을 갱신한다")
    @Test
    void updatePeakAssetValue() {
        EquityTracker tracker = tracker();
        tracker.record(
                FIRST_VALUATION_AT,
                BigDecimal.valueOf(1_200_000),
                BigDecimal.valueOf(100_000)
        );
        tracker.record(
                FIRST_VALUATION_AT.plusSeconds(3_600),
                BigDecimal.valueOf(1_300_000),
                BigDecimal.valueOf(100_000)
        );

        EquitySnapshot snapshot = tracker.record(
                FIRST_VALUATION_AT.plusSeconds(7_200),
                BigDecimal.valueOf(1_170_000),
                BigDecimal.valueOf(100_000)
        );

        assertThat(snapshot.drawdownRate()).isEqualByComparingTo("0.10000000");
    }

    @DisplayName("같은 평가 시각을 중복 기록할 수 없다")
    @Test
    void rejectDuplicateValuationTime() {
        EquityTracker tracker = tracker();
        tracker.record(
                FIRST_VALUATION_AT,
                BigDecimal.valueOf(1_000_000),
                BigDecimal.valueOf(100_000)
        );

        assertThatIllegalArgumentException()
                .isThrownBy(() -> tracker.record(
                        FIRST_VALUATION_AT,
                        BigDecimal.valueOf(1_000_000),
                        BigDecimal.valueOf(100_000)
                ));
    }

    @DisplayName("이전 평가 시각보다 과거인 시각을 기록할 수 없다")
    @Test
    void rejectEarlierValuationTime() {
        EquityTracker tracker = tracker();
        tracker.record(
                FIRST_VALUATION_AT,
                BigDecimal.valueOf(1_000_000),
                BigDecimal.valueOf(100_000)
        );

        assertThatIllegalArgumentException()
                .isThrownBy(() -> tracker.record(
                        FIRST_VALUATION_AT.minusSeconds(3_600),
                        BigDecimal.valueOf(1_000_000),
                        BigDecimal.valueOf(100_000)
                ));
    }

    @DisplayName("실패한 평가는 마지막 시각과 고점 상태를 변경하지 않는다")
    @Test
    void preserveStateWhenRecordingFails() {
        EquityTracker tracker = tracker();
        tracker.record(
                FIRST_VALUATION_AT,
                BigDecimal.valueOf(1_200_000),
                BigDecimal.valueOf(100_000)
        );
        Instant secondValuationAt = FIRST_VALUATION_AT.plusSeconds(3_600);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> tracker.record(
                        secondValuationAt,
                        BigDecimal.valueOf(1_300_000),
                        BigDecimal.valueOf(1_400_000)
                ));

        EquitySnapshot snapshot = tracker.record(
                secondValuationAt,
                BigDecimal.valueOf(1_100_000),
                BigDecimal.valueOf(100_000)
        );
        assertThat(snapshot.drawdownRate()).isEqualByComparingTo("0.08333333");
    }

    @DisplayName("초기 자산은 0보다 커야 한다")
    @Test
    void rejectZeroInitialAssetValue() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new EquityTracker(
                        BigDecimal.ZERO,
                        new CumulativeReturnCalculator(),
                        new DrawdownRateCalculator()
                ));
    }

    private EquityTracker tracker() {
        return new EquityTracker(
                BigDecimal.valueOf(1_000_000),
                new CumulativeReturnCalculator(),
                new DrawdownRateCalculator()
        );
    }
}
