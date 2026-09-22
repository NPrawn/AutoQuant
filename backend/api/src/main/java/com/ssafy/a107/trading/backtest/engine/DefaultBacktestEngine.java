package com.ssafy.a107.trading.backtest.engine;

import com.ssafy.a107.asset.domain.Asset;
import com.ssafy.a107.candle.domain.MinuteCandle;
import com.ssafy.a107.strategy.engine.StrategyExecutionContext;
import com.ssafy.a107.strategy.engine.StrategyExecutionPipeline;
import com.ssafy.a107.strategy.engine.portfolio.TargetAssetWeight;
import com.ssafy.a107.strategy.engine.portfolio.TargetPortfolio;
import com.ssafy.a107.trading.backtest.domain.BacktestRun;
import com.ssafy.a107.trading.backtest.domain.BacktestFailureCode;
import com.ssafy.a107.trading.backtest.domain.BacktestUniverseAsset;
import com.ssafy.a107.trading.backtest.domain.BacktestUniverseAssetId;
import com.ssafy.a107.trading.backtest.engine.execution.BacktestExecutionPolicyResolver;
import com.ssafy.a107.trading.backtest.engine.execution.BacktestVirtualOrderFactory;
import com.ssafy.a107.trading.backtest.engine.execution.VirtualExecutionPolicy;
import com.ssafy.a107.trading.backtest.engine.execution.VirtualOrder;
import com.ssafy.a107.trading.backtest.engine.marketdata.BacktestCandleTimeline;
import com.ssafy.a107.trading.backtest.engine.marketdata.BacktestCandleWindow;
import com.ssafy.a107.trading.backtest.engine.marketdata.BacktestCandleWindowProvider;
import com.ssafy.a107.trading.backtest.engine.marketdata.BacktestExecutionCandleResolver;
import com.ssafy.a107.trading.backtest.engine.performance.CumulativeReturnCalculator;
import com.ssafy.a107.trading.backtest.engine.performance.DrawdownRateCalculator;
import com.ssafy.a107.trading.backtest.engine.performance.EquitySnapshot;
import com.ssafy.a107.trading.backtest.engine.performance.EquityTracker;
import com.ssafy.a107.trading.backtest.engine.performance.PerformanceCalculationInput;
import com.ssafy.a107.trading.backtest.engine.performance.PerformanceReport;
import com.ssafy.a107.trading.backtest.engine.performance.PerformanceReportCalculator;
import com.ssafy.a107.trading.backtest.engine.portfolio.BacktestOrderExecutionResult;
import com.ssafy.a107.trading.backtest.engine.portfolio.BacktestPortfolio;
import com.ssafy.a107.trading.backtest.engine.portfolio.BacktestPortfolioOrderExecutionService;
import com.ssafy.a107.trading.backtest.engine.portfolio.BacktestRebalanceOrder;
import com.ssafy.a107.trading.backtest.engine.portfolio.BacktestRebalanceOrderPlanner;
import com.ssafy.a107.trading.backtest.engine.portfolio.PortfolioValuationCalculator;
import com.ssafy.a107.trading.backtest.engine.portfolio.RealizedTradeResult;
import com.ssafy.a107.trading.backtest.engine.universe.BacktestUniverseResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@Profile("backtest-worker")
public class DefaultBacktestEngine implements BacktestEngine {

    private static final Logger log = LoggerFactory.getLogger(DefaultBacktestEngine.class);

    private final BacktestUniverseResolver backtestUniverseResolver;
    private final BacktestCandleWindowProvider backtestCandleWindowProvider;
    private final BacktestStrategyExecutionContextFactory contextFactory;
    private final StrategyExecutionPipeline strategyExecutionPipeline;
    private final BacktestRebalanceSchedule backtestRebalanceSchedule;
    private final BacktestRebalanceOrderPlanner backtestRebalanceOrderPlanner;
    private final BacktestExecutionCandleResolver executionCandleResolver;
    private final BacktestVirtualOrderFactory virtualOrderFactory;
    private final BacktestPortfolioOrderExecutionService orderExecutionService;
    private final BacktestExecutionPolicyResolver executionPolicyResolver;
    private final PortfolioValuationCalculator portfolioValuationCalculator;
    private final PerformanceReportCalculator performanceReportCalculator;
    private final CumulativeReturnCalculator cumulativeReturnCalculator;
    private final DrawdownRateCalculator drawdownRateCalculator;

    public DefaultBacktestEngine(
            BacktestUniverseResolver backtestUniverseResolver,
            BacktestCandleWindowProvider backtestCandleWindowProvider,
            BacktestStrategyExecutionContextFactory contextFactory,
            StrategyExecutionPipeline strategyExecutionPipeline,
            BacktestRebalanceSchedule backtestRebalanceSchedule,
            BacktestRebalanceOrderPlanner backtestRebalanceOrderPlanner,
            BacktestExecutionCandleResolver executionCandleResolver,
            BacktestVirtualOrderFactory virtualOrderFactory,
            BacktestPortfolioOrderExecutionService orderExecutionService,
            BacktestExecutionPolicyResolver executionPolicyResolver,
            PortfolioValuationCalculator portfolioValuationCalculator,
            PerformanceReportCalculator performanceReportCalculator,
            CumulativeReturnCalculator cumulativeReturnCalculator,
            DrawdownRateCalculator drawdownRateCalculator
    ) {
        this.backtestUniverseResolver = Objects.requireNonNull(backtestUniverseResolver);
        this.backtestCandleWindowProvider = Objects.requireNonNull(backtestCandleWindowProvider);
        this.contextFactory = Objects.requireNonNull(contextFactory);
        this.strategyExecutionPipeline = Objects.requireNonNull(strategyExecutionPipeline);
        this.backtestRebalanceSchedule = Objects.requireNonNull(backtestRebalanceSchedule);
        this.backtestRebalanceOrderPlanner = Objects.requireNonNull(backtestRebalanceOrderPlanner);
        this.executionCandleResolver = Objects.requireNonNull(executionCandleResolver);
        this.virtualOrderFactory = Objects.requireNonNull(virtualOrderFactory);
        this.orderExecutionService = Objects.requireNonNull(orderExecutionService);
        this.executionPolicyResolver = Objects.requireNonNull(executionPolicyResolver);
        this.portfolioValuationCalculator = Objects.requireNonNull(portfolioValuationCalculator);
        this.performanceReportCalculator = Objects.requireNonNull(performanceReportCalculator);
        this.cumulativeReturnCalculator = Objects.requireNonNull(cumulativeReturnCalculator);
        this.drawdownRateCalculator = Objects.requireNonNull(drawdownRateCalculator);
    }

    @Override
    public BacktestExecutionResult execute(BacktestRun backtestRun) {
        Objects.requireNonNull(backtestRun);

        long engineStartedAt = System.nanoTime();
        long candidateResolveStartedAt = System.nanoTime();
        List<Asset> candidateUniverse = backtestUniverseResolver
                .resolveCandidates(backtestRun);
        long candidateResolveElapsedNanos = System.nanoTime() - candidateResolveStartedAt;
        Map<UUID, Asset> assets = assetsById(candidateUniverse);
        if (assets.isEmpty()) {
            throw new BacktestExecutionException(
                    BacktestFailureCode.EMPTY_UNIVERSE,
                    "Backtest universe must not be empty"
            );
        }

        long candleLoadStartedAt = System.nanoTime();
        BacktestCandleWindow candleWindow = backtestCandleWindowProvider.load(
                backtestRun,
                assets.keySet()
        );
        long candleLoadElapsedNanos = System.nanoTime() - candleLoadStartedAt;
        BacktestCandleTimeline candleTimeline = BacktestCandleTimeline.from(
                candleWindow
        );
        if (candleTimeline.evaluationCandleOpenTimes().isEmpty()) {
            throw new BacktestExecutionException(
                    BacktestFailureCode.INSUFFICIENT_CANDLES,
                    "Backtest evaluation candles must not be empty"
            );
        }

        BacktestPortfolio portfolio = new BacktestPortfolio(
                backtestRun.getInitialCapital()
        );
        EquityTracker equityTracker = new EquityTracker(
                backtestRun.getInitialCapital(),
                cumulativeReturnCalculator,
                drawdownRateCalculator
        );
        List<EquitySnapshot> equitySnapshots = new ArrayList<>();
        equitySnapshots.add(equityTracker.record(
                backtestRun.getPeriodStart(),
                backtestRun.getInitialCapital(),
                backtestRun.getInitialCapital()
        ));

        VirtualExecutionPolicy executionPolicy = executionPolicyResolver.resolve(
                backtestRun
        );
        List<com.ssafy.a107.trading.backtest.engine.execution.VirtualFill> fills =
                new ArrayList<>();
        List<RealizedTradeResult> realizedTrades = new ArrayList<>();
        List<BacktestUniverseAsset> universeAssets = new ArrayList<>();
        Map<UUID, BigDecimal> finalPrices = Map.of();
        long rebalanceUniverseElapsedNanos = 0L;
        long strategyExecutionElapsedNanos = 0L;
        long orderExecutionElapsedNanos = 0L;
        int rebalanceCount = 0;

        List<Instant> candleOpenTimes = candleTimeline.evaluationCandleOpenTimes();
        for (int index = 0; index < candleOpenTimes.size(); index++) {
            Instant candleOpenTime = candleOpenTimes.get(index);
            Map<UUID, BigDecimal> closePrices = executionCandleResolver
                    .resolveClosePrices(candleWindow, candleOpenTime);
            Map<UUID, BigDecimal> valuationPrices = new HashMap<>(closePrices);

            if (backtestRebalanceSchedule.shouldRebalance(
                    backtestRun.getRebalanceInterval(),
                    backtestRun.getTimeframe(),
                    candleOpenTime,
                    index == 0
            )) {
                rebalanceCount++;
                long rebalanceUniverseStartedAt = System.nanoTime();
                List<Asset> rebalanceUniverse = resolveRebalanceUniverse(
                        backtestRun,
                        candleOpenTime,
                        assets
                );
                rebalanceUniverseElapsedNanos += System.nanoTime() - rebalanceUniverseStartedAt;

                long strategyExecutionStartedAt = System.nanoTime();
                TargetPortfolio targetPortfolio = executeStrategy(
                        backtestRun,
                        candleWindow,
                        candleOpenTime,
                        rebalanceUniverse
                );
                strategyExecutionElapsedNanos += System.nanoTime() - strategyExecutionStartedAt;
                Instant signalAt = candleOpenTime.plus(candleDuration(backtestRun));

                universeAssets.addAll(snapshotUniverse(
                        backtestRun,
                        signalAt,
                        rebalanceUniverse,
                        targetPortfolio
                ));

                long orderExecutionStartedAt = System.nanoTime();
                List<VirtualOrder> orders = backtestRebalanceOrderPlanner.plan(
                                portfolio,
                                targetPortfolio,
                                closePrices
                        ).stream()
                        .map(order -> toVirtualOrder(
                                order,
                                signalAt,
                                candleWindow
                        ))
                        .flatMap(java.util.Optional::stream)
                        .toList();

                BacktestOrderExecutionResult executionResult =
                        orderExecutionService.execute(
                                portfolio,
                                orders,
                                executionPolicy
                        );
                fills.addAll(executionResult.fills());
                realizedTrades.addAll(executionResult.realizedTrades());
                executionResult.fills().forEach(fill -> valuationPrices.put(
                        fill.order().assetId(),
                        fill.executedPrice()
                ));
                orderExecutionElapsedNanos += System.nanoTime() - orderExecutionStartedAt;
            }

            BigDecimal totalAssetValue = portfolioValuationCalculator
                    .calculateTotalAssetValue(portfolio, valuationPrices);
            Instant valuationAt = candleOpenTime.plus(candleDuration(backtestRun));
            equitySnapshots.add(equityTracker.record(
                    valuationAt,
                    totalAssetValue,
                    portfolio.getCashBalance()
            ));
            finalPrices = Map.copyOf(valuationPrices);
        }

        long performanceReportStartedAt = System.nanoTime();
        PerformanceReport performanceReport = performanceReportCalculator.calculate(
                new PerformanceCalculationInput(
                        backtestRun.getInitialCapital(),
                        backtestRun.getPeriodStart(),
                        backtestRun.getPeriodEnd(),
                        equitySnapshots,
                        fills,
                        realizedTrades,
                        portfolio.getPositions(),
                        finalPrices,
                        periodsPerYear(backtestRun),
                        null,
                        null
                )
        );
        long performanceReportElapsedNanos = System.nanoTime() - performanceReportStartedAt;

        log.info(
                "Backtest engine timing backtestId={} candidates={} evaluationCandles={} rebalances={} candidateResolveMs={} candleLoadMs={} rebalanceUniverseMs={} strategyExecutionMs={} orderExecutionMs={} performanceReportMs={} totalMs={}",
                backtestRun.getBacktestId(),
                assets.size(),
                candleOpenTimes.size(),
                rebalanceCount,
                toMillis(candidateResolveElapsedNanos),
                toMillis(candleLoadElapsedNanos),
                toMillis(rebalanceUniverseElapsedNanos),
                toMillis(strategyExecutionElapsedNanos),
                toMillis(orderExecutionElapsedNanos),
                toMillis(performanceReportElapsedNanos),
                elapsedMillis(engineStartedAt)
        );

        return new BacktestExecutionResult(
                performanceReport,
                fills,
                assets,
                universeAssets
        );
    }

    private Map<UUID, Asset> assetsById(List<Asset> assets) {
        return assets.stream()
                .collect(Collectors.toMap(
                        Asset::getAssetId,
                        asset -> asset,
                        (left, right) -> {
                            throw new IllegalArgumentException(
                                    "Backtest universe must not contain duplicate assets"
                            );
                        },
                        LinkedHashMap::new
                ));
    }

    private List<Asset> resolveRebalanceUniverse(
            BacktestRun backtestRun,
            Instant candleOpenTime,
            Map<UUID, Asset> availableAssets
    ) {
        return backtestUniverseResolver.resolve(backtestRun, candleOpenTime)
                .stream()
                .filter(asset -> availableAssets.containsKey(asset.getAssetId()))
                .toList();
    }

    private TargetPortfolio executeStrategy(
            BacktestRun backtestRun,
            BacktestCandleWindow candleWindow,
            Instant candleOpenTime,
            List<Asset> rebalanceUniverse
    ) {
        StrategyExecutionContext context = contextFactory.create(
                backtestRun,
                candleWindow,
                candleOpenTime,
                rebalanceUniverse.stream()
                        .map(Asset::getAssetId)
                        .toList()
        );

        return strategyExecutionPipeline.executeExcludingInsufficientCandleAssets(context);
    }

    private List<BacktestUniverseAsset> snapshotUniverse(
            BacktestRun backtestRun,
            Instant rebalanceAt,
            List<Asset> rebalanceUniverse,
            TargetPortfolio targetPortfolio
    ) {
        Map<UUID, Short> ranksByAssetId = new HashMap<>();
        List<TargetAssetWeight> targetWeights = targetPortfolio.assetWeights();
        for (int index = 0; index < targetWeights.size(); index++) {
            ranksByAssetId.put(
                    targetWeights.get(index).assetId(),
                    (short) (index + 1)
            );
        }

        return rebalanceUniverse.stream()
                .sorted(Comparator.comparing(Asset::getAssetId))
                .map(asset -> new BacktestUniverseAsset(
                        new BacktestUniverseAssetId(
                                backtestRun.getBacktestId(),
                                rebalanceAt,
                                asset.getAssetId()
                        ),
                        backtestRun,
                        asset,
                        ranksByAssetId.get(asset.getAssetId()),
                        ranksByAssetId.containsKey(asset.getAssetId())
                ))
                .toList();
    }

    private java.util.Optional<VirtualOrder> toVirtualOrder(
            BacktestRebalanceOrder order,
            Instant signalAt,
            BacktestCandleWindow candleWindow
    ) {
        return executionCandleResolver.findNextExecutionCandle(
                        candleWindow,
                        order.assetId(),
                        signalAt.minus(candleDuration(candleWindow.timeframe()))
                )
                .map(candle -> virtualOrderFactory.create(
                        order,
                        signalAt,
                        candle
                ));
    }

    private Duration candleDuration(BacktestRun backtestRun) {
        return candleDuration(backtestRun.getTimeframe());
    }

    private Duration candleDuration(com.ssafy.a107.strategy.domain.dsl.Timeframe timeframe) {
        return Duration.ofMinutes(timeframe.getIntervalMinutes());
    }

    private int periodsPerYear(BacktestRun backtestRun) {
        return switch (backtestRun.getTimeframe()) {
            case ONE_HOUR -> 24 * 365;
            case FOUR_HOURS -> 6 * 365;
            case ONE_DAY -> 365;
        };
    }

    private long elapsedMillis(long startedAt) {
        return toMillis(System.nanoTime() - startedAt);
    }

    private long toMillis(long elapsedNanos) {
        return Duration.ofNanos(elapsedNanos).toMillis();
    }
}
