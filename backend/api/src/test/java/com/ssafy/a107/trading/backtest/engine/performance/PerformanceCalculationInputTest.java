package com.ssafy.a107.trading.backtest.engine.performance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class PerformanceCalculationInputTest {

    @DisplayName("초기 자본금은 0보다 커야 한다")
    @Test
    void rejectNonPositiveInitialCapital() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> input(BigDecimal.ZERO, List.of(
                        snapshot("2026-01-01T00:00:00Z"),
                        snapshot("2026-01-02T00:00:00Z")
                ), null, null));
    }

    @DisplayName("성과 계산에는 두 개 이상의 자산 스냅샷이 필요하다")
    @Test
    void rejectTooFewEquitySnapshots() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> input(
                        BigDecimal.ONE,
                        List.of(snapshot("2026-01-01T00:00:00Z")),
                        null,
                        null
                ));
    }

    @DisplayName("벤치마크 시작가와 종료가는 함께 제공해야 한다")
    @Test
    void rejectUnpairedBenchmarkPrices() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> input(
                        BigDecimal.ONE,
                        List.of(
                                snapshot("2026-01-01T00:00:00Z"),
                                snapshot("2026-01-02T00:00:00Z")
                        ),
                        BigDecimal.ONE,
                        null
                ));
    }

    private PerformanceCalculationInput input(
            BigDecimal initialCapital,
            List<EquitySnapshot> snapshots,
            BigDecimal initialBenchmarkPrice,
            BigDecimal finalBenchmarkPrice
    ) {
        return new PerformanceCalculationInput(
                initialCapital,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-02-01T00:00:00Z"),
                snapshots,
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                365,
                initialBenchmarkPrice,
                finalBenchmarkPrice
        );
    }

    private EquitySnapshot snapshot(String valuationAt) {
        return new EquitySnapshot(
                Instant.parse(valuationAt),
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );
    }
}
