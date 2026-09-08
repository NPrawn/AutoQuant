package com.ssafy.a107.trading.backtest.engine.performance;

import com.ssafy.a107.trading.backtest.engine.portfolio.RealizedTradeResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Component
public class PerformanceReportCalculator {

    private final CumulativeReturnCalculator cumulativeReturnCalculator;
    private final CagrCalculator cagrCalculator;
    private final MaximumDrawdownCalculator maximumDrawdownCalculator;
    private final PeriodicReturnCalculator periodicReturnCalculator;
    private final VolatilityCalculator volatilityCalculator;
    private final SharpeRatioCalculator sharpeRatioCalculator;
    private final WinRateCalculator winRateCalculator;
    private final TradeStatisticsCalculator tradeStatisticsCalculator;
    private final CostComparisonCalculator costComparisonCalculator;
    private final AssetContributionCalculator assetContributionCalculator;
    private final BenchmarkReturnCalculator benchmarkReturnCalculator;
    private final ExcessReturnCalculator excessReturnCalculator;

    public PerformanceReportCalculator(
            CumulativeReturnCalculator cumulativeReturnCalculator,
            CagrCalculator cagrCalculator,
            MaximumDrawdownCalculator maximumDrawdownCalculator,
            PeriodicReturnCalculator periodicReturnCalculator,
            VolatilityCalculator volatilityCalculator,
            SharpeRatioCalculator sharpeRatioCalculator,
            WinRateCalculator winRateCalculator,
            TradeStatisticsCalculator tradeStatisticsCalculator,
            CostComparisonCalculator costComparisonCalculator,
            AssetContributionCalculator assetContributionCalculator,
            BenchmarkReturnCalculator benchmarkReturnCalculator,
            ExcessReturnCalculator excessReturnCalculator
    ) {
        this.cumulativeReturnCalculator = cumulativeReturnCalculator;
        this.cagrCalculator = cagrCalculator;
        this.maximumDrawdownCalculator = maximumDrawdownCalculator;
        this.periodicReturnCalculator = periodicReturnCalculator;
        this.volatilityCalculator = volatilityCalculator;
        this.sharpeRatioCalculator = sharpeRatioCalculator;
        this.winRateCalculator = winRateCalculator;
        this.tradeStatisticsCalculator = tradeStatisticsCalculator;
        this.costComparisonCalculator = costComparisonCalculator;
        this.assetContributionCalculator = assetContributionCalculator;
        this.benchmarkReturnCalculator = benchmarkReturnCalculator;
        this.excessReturnCalculator = excessReturnCalculator;
    }

    public PerformanceReport calculate(PerformanceCalculationInput input) {
        Objects.requireNonNull(input);

        List<EquitySnapshot> equitySnapshots = input.equitySnapshots();
        EquitySnapshot finalSnapshot = equitySnapshots.getLast();
        BigDecimal finalAssetValue = finalSnapshot.totalAssetValue();

        BigDecimal cumulativeReturn = cumulativeReturnCalculator.calculate(
                input.initialCapital(),
                finalAssetValue
        );
        BigDecimal cagr = cagrCalculator.calculate(
                input.initialCapital(),
                finalAssetValue,
                input.periodStart(),
                input.periodEnd()
        );
        BigDecimal maximumDrawdown = maximumDrawdownCalculator.calculate(
                equitySnapshots
        );
        List<BigDecimal> periodicReturns = periodicReturnCalculator.calculate(
                equitySnapshots
        );

        Optional<BigDecimal> volatility = calculateVolatility(
                periodicReturns,
                input.periodsPerYear()
        );
        Optional<BigDecimal> sharpeRatio = calculateSharpeRatio(
                periodicReturns,
                input.periodsPerYear(),
                volatility
        );

        TradeStatistics tradeStatistics = tradeStatisticsCalculator.calculate(
                input.fills()
        );
        Optional<BigDecimal> winRate = winRateCalculator.calculate(
                input.realizedTrades().stream()
                        .map(RealizedTradeResult::realizedProfitLoss)
                        .toList()
        );
        CostComparison costComparison = costComparisonCalculator.calculate(
                input.initialCapital(),
                finalAssetValue,
                tradeStatistics
        );

        Optional<BigDecimal> benchmarkReturn = calculateBenchmarkReturn(input);
        Optional<BigDecimal> excessReturn = benchmarkReturn.map(
                value -> excessReturnCalculator.calculate(
                        cumulativeReturn,
                        value
                )
        );

        return new PerformanceReport(
                finalAssetValue,
                cumulativeReturn,
                cagr,
                maximumDrawdown,
                sharpeRatio,
                volatility,
                winRate,
                benchmarkReturn,
                excessReturn,
                tradeStatistics,
                costComparison,
                assetContributionCalculator.calculate(
                        input.initialCapital(),
                        input.realizedTrades(),
                        input.openPositions(),
                        input.finalPrices()
                ),
                equitySnapshots
        );
    }

    private Optional<BigDecimal> calculateVolatility(
            List<BigDecimal> periodicReturns,
            int periodsPerYear
    ) {
        if (periodicReturns.size() < 2) {
            return Optional.empty();
        }

        return Optional.of(volatilityCalculator.calculateAnnualized(
                periodicReturns,
                periodsPerYear
        ));
    }

    private Optional<BigDecimal> calculateSharpeRatio(
            List<BigDecimal> periodicReturns,
            int periodsPerYear,
            Optional<BigDecimal> volatility
    ) {
        if (periodicReturns.size() < 2
                || volatility.isEmpty()
                || volatility.get().signum() == 0) {
            return Optional.empty();
        }

        return Optional.of(sharpeRatioCalculator.calculateAnnualized(
                periodicReturns,
                periodsPerYear
        ));
    }

    private Optional<BigDecimal> calculateBenchmarkReturn(
            PerformanceCalculationInput input
    ) {
        if (input.initialBenchmarkPrice() == null) {
            return Optional.empty();
        }

        return Optional.of(benchmarkReturnCalculator.calculate(
                input.initialBenchmarkPrice(),
                input.finalBenchmarkPrice()
        ));
    }
}
