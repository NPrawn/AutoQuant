package com.ssafy.a107.trading.backtest.engine.performance;

import com.ssafy.a107.trading.backtest.domain.PositionSide;
import com.ssafy.a107.trading.backtest.domain.TradeSide;
import com.ssafy.a107.trading.backtest.engine.execution.VirtualFill;
import com.ssafy.a107.trading.backtest.engine.execution.VirtualOrder;
import com.ssafy.a107.trading.backtest.engine.portfolio.RealizedTradeResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PerformanceReportCalculatorTest {

    private static final UUID ASSET_ID = UUID.fromString(
            "11111111-1111-1111-1111-111111111111"
    );

    private final PerformanceReportCalculator calculator =
            new PerformanceReportCalculator(
                    new CumulativeReturnCalculator(),
                    new CagrCalculator(),
                    new MaximumDrawdownCalculator(),
                    new PeriodicReturnCalculator(),
                    new VolatilityCalculator(),
                    new SharpeRatioCalculator(),
                    new WinRateCalculator(),
                    new TradeStatisticsCalculator(),
                    new CostComparisonCalculator(
                            new CumulativeReturnCalculator()
                    ),
                    new AssetContributionCalculator(),
                    new BenchmarkReturnCalculator(
                            new CumulativeReturnCalculator()
                    ),
                    new ExcessReturnCalculator()
            );

    @DisplayName("백테스트 체결과 자산 스냅샷으로 성과 보고서를 계산한다")
    @Test
    void calculatePerformanceReport() {
        PerformanceReport report = calculator.calculate(
                new PerformanceCalculationInput(
                        new BigDecimal("1000000"),
                        List.of(
                                snapshot("2026-01-01T00:00:00Z", "1000000", "0"),
                                snapshot("2026-07-01T00:00:00Z", "1200000", "0"),
                                snapshot(
                                        "2027-01-01T00:00:00Z",
                                        "1100000",
                                        "0.08333333"
                                )
                        ),
                        List.of(fill()),
                        List.of(new RealizedTradeResult(
                                ASSET_ID,
                                BigDecimal.ONE,
                                new BigDecimal("120000"),
                                new BigDecimal("100000")
                        )),
                        List.of(),
                        Map.of(),
                        365,
                        new BigDecimal("100"),
                        new BigDecimal("108")
                )
        );

        assertThat(report.finalAssetValue())
                .isEqualByComparingTo("1100000");
        assertThat(report.cumulativeReturn())
                .isEqualByComparingTo("0.10000000");
        assertThat(report.cagr()).isEqualByComparingTo("0.10000000");
        assertThat(report.maximumDrawdown())
                .isEqualByComparingTo("0.08333333");
        assertThat(report.volatility()).isPresent();
        assertThat(report.sharpeRatio()).isPresent();
        assertThat(report.winRate()).contains(new BigDecimal("1.00000000"));
        assertThat(report.benchmarkReturn())
                .contains(new BigDecimal("0.08000000"));
        assertThat(report.excessReturn())
                .contains(new BigDecimal("0.02000000"));
        assertThat(report.tradeStatistics().totalFee())
                .isEqualByComparingTo("100.00000000");
        assertThat(report.tradeStatistics().totalSlippage())
                .isEqualByComparingTo("200.00000000");
        assertThat(report.costComparison().finalAssetValueBeforeCosts())
                .isEqualByComparingTo("1100300.00000000");
        assertThat(report.assetContributions()).singleElement()
                .satisfies(contribution -> {
                    assertThat(contribution.assetId()).isEqualTo(ASSET_ID);
                    assertThat(contribution.profitLoss())
                            .isEqualByComparingTo("20000.00000000");
                });
    }

    @DisplayName("반환 표본이 부족하면 변동성과 샤프 비율을 제공하지 않는다")
    @Test
    void omitVolatilityAndSharpeWithInsufficientPeriodicReturns() {
        PerformanceReport report = calculator.calculate(
                new PerformanceCalculationInput(
                        new BigDecimal("1000000"),
                        List.of(
                                snapshot("2026-01-01T00:00:00Z", "1000000", "0"),
                                snapshot("2027-01-01T00:00:00Z", "1100000", "0")
                        ),
                        List.of(),
                        List.of(),
                        List.of(),
                        Map.of(),
                        365,
                        null,
                        null
                )
        );

        assertThat(report.volatility()).isEmpty();
        assertThat(report.sharpeRatio()).isEmpty();
        assertThat(report.benchmarkReturn()).isEmpty();
        assertThat(report.excessReturn()).isEmpty();
    }

    private EquitySnapshot snapshot(
            String valuationAt,
            String totalAssetValue,
            String drawdownRate
    ) {
        return new EquitySnapshot(
                Instant.parse(valuationAt),
                new BigDecimal(totalAssetValue),
                new BigDecimal(totalAssetValue),
                BigDecimal.ZERO,
                new BigDecimal(drawdownRate)
        );
    }

    private VirtualFill fill() {
        VirtualOrder order = new VirtualOrder(
                ASSET_ID,
                PositionSide.LONG,
                TradeSide.BUY,
                Instant.parse("2026-06-30T23:00:00Z"),
                Instant.parse("2026-07-01T00:00:00Z"),
                new BigDecimal("100000"),
                new BigDecimal("100000")
        );

        return new VirtualFill(
                order,
                new BigDecimal("100000"),
                BigDecimal.ONE,
                new BigDecimal("100000"),
                new BigDecimal("100"),
                new BigDecimal("200")
        );
    }
}
