package com.ssafy.a107.trading.backtest.engine.performance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class PerformanceReportTest {

    private static final Instant VALUATION_AT = Instant.parse(
            "2026-01-02T00:00:00Z"
    );

    @DisplayName("성과 계산 결과를 하나의 보고서로 생성한다")
    @Test
    void createPerformanceReport() {
        PerformanceReport report = report(
                new BigDecimal("1100000"),
                Optional.of(new BigDecimal("0.1")),
                Optional.of(new BigDecimal("0.02"))
        );

        assertThat(report.finalAssetValue())
                .isEqualByComparingTo("1100000");
        assertThat(report.benchmarkReturn())
                .contains(new BigDecimal("0.1"));
        assertThat(report.excessReturn())
                .contains(new BigDecimal("0.02"));
        assertThat(report.equitySnapshots()).hasSize(1);
    }

    @DisplayName("Benchmark 수익률과 초과수익률은 함께 존재해야 한다")
    @Test
    void rejectUnpairedBenchmarkAndExcessReturns() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> report(
                        new BigDecimal("1100000"),
                        Optional.of(new BigDecimal("0.1")),
                        Optional.empty()
                ));
    }

    @DisplayName("성과 보고서에는 적어도 하나의 Equity 스냅샷이 필요하다")
    @Test
    void rejectEmptyEquitySnapshots() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new PerformanceReport(
                        new BigDecimal("1100000"),
                        new BigDecimal("0.1"),
                        new BigDecimal("0.1"),
                        BigDecimal.ZERO,
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        tradeStatistics(),
                        costComparison(),
                        List.of(),
                        List.of()
                ));
    }

    @DisplayName("최종자산은 마지막 Equity 스냅샷의 평가자산과 일치해야 한다")
    @Test
    void rejectMismatchedFinalAssetValue() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> report(
                        new BigDecimal("1200000"),
                        Optional.empty(),
                        Optional.empty()
                ));
    }

    private PerformanceReport report(
            BigDecimal finalAssetValue,
            Optional<BigDecimal> benchmarkReturn,
            Optional<BigDecimal> excessReturn
    ) {
        return new PerformanceReport(
                finalAssetValue,
                new BigDecimal("0.1"),
                new BigDecimal("0.1"),
                new BigDecimal("0.05"),
                Optional.of(new BigDecimal("1.2")),
                Optional.of(new BigDecimal("0.2")),
                Optional.of(new BigDecimal("0.5")),
                benchmarkReturn,
                excessReturn,
                tradeStatistics(),
                costComparison(),
                List.of(new AssetContribution(
                        UUID.fromString("11111111-1111-1111-1111-111111111111"),
                        new BigDecimal("100000"),
                        new BigDecimal("0.1")
                )),
                List.of(new EquitySnapshot(
                        VALUATION_AT,
                        new BigDecimal("1100000"),
                        new BigDecimal("1100000"),
                        new BigDecimal("0.1"),
                        new BigDecimal("0.05")
                ))
        );
    }

    private TradeStatistics tradeStatistics() {
        return new TradeStatistics(
                1,
                new BigDecimal("1000"),
                new BigDecimal("2000")
        );
    }

    private CostComparison costComparison() {
        return new CostComparison(
                new BigDecimal("1103000"),
                new BigDecimal("1100000"),
                new BigDecimal("1000"),
                new BigDecimal("2000"),
                new BigDecimal("0.103"),
                new BigDecimal("0.1"),
                new BigDecimal("0.003")
        );
    }
}
