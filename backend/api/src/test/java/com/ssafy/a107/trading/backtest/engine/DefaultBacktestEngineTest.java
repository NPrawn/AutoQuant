package com.ssafy.a107.trading.backtest.engine;

import com.ssafy.a107.asset.domain.Asset;
import com.ssafy.a107.candle.domain.MinuteCandle;
import com.ssafy.a107.candle.domain.MinuteCandleFixture;
import com.ssafy.a107.strategy.domain.dsl.RebalancePeriod;
import com.ssafy.a107.strategy.domain.dsl.Timeframe;
import com.ssafy.a107.strategy.engine.StrategyExecutionContext;
import com.ssafy.a107.strategy.engine.StrategyExecutionPipeline;
import com.ssafy.a107.strategy.engine.portfolio.TargetAssetWeight;
import com.ssafy.a107.strategy.engine.portfolio.TargetPortfolio;
import com.ssafy.a107.trading.backtest.domain.BacktestRun;
import com.ssafy.a107.trading.backtest.engine.execution.BacktestExecutionPolicyResolver;
import com.ssafy.a107.trading.backtest.engine.execution.BacktestVirtualOrderFactory;
import com.ssafy.a107.trading.backtest.engine.execution.VirtualExecutionPolicy;
import com.ssafy.a107.trading.backtest.engine.marketdata.BacktestCandleWindow;
import com.ssafy.a107.trading.backtest.engine.marketdata.BacktestCandleWindowProvider;
import com.ssafy.a107.trading.backtest.engine.marketdata.BacktestExecutionCandleResolver;
import com.ssafy.a107.trading.backtest.engine.performance.CostComparison;
import com.ssafy.a107.trading.backtest.engine.performance.CumulativeReturnCalculator;
import com.ssafy.a107.trading.backtest.engine.performance.DrawdownRateCalculator;
import com.ssafy.a107.trading.backtest.engine.performance.PerformanceCalculationInput;
import com.ssafy.a107.trading.backtest.engine.performance.PerformanceReport;
import com.ssafy.a107.trading.backtest.engine.performance.PerformanceReportCalculator;
import com.ssafy.a107.trading.backtest.engine.performance.TradeStatistics;
import com.ssafy.a107.trading.backtest.engine.portfolio.BacktestOrderExecutionResult;
import com.ssafy.a107.trading.backtest.engine.portfolio.BacktestPortfolioOrderExecutionService;
import com.ssafy.a107.trading.backtest.engine.portfolio.BacktestRebalanceOrderPlanner;
import com.ssafy.a107.trading.backtest.engine.portfolio.PortfolioValuationCalculator;
import com.ssafy.a107.trading.backtest.engine.universe.BacktestUniverseResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultBacktestEngineTest {

    private static final UUID BACKTEST_ID = UUID.fromString(
            "11111111-1111-1111-1111-111111111111"
    );
    private static final UUID ASSET_ID = UUID.fromString(
            "22222222-2222-2222-2222-222222222222"
    );
    private static final UUID INSUFFICIENT_CANDLE_ASSET_ID = UUID.fromString(
            "33333333-3333-3333-3333-333333333333"
    );
    private static final Instant PERIOD_START = Instant.parse(
            "2026-01-02T00:00:00Z"
    );
    private static final Instant PERIOD_END = Instant.parse(
            "2026-01-02T02:00:00Z"
    );

    @DisplayName("거래가 없는 백테스트도 유니버스와 equity 결과를 생성한다")
    @Test
    void executeBacktestWithoutTrades() {
        BacktestUniverseResolver universeResolver = mock(
                BacktestUniverseResolver.class
        );
        BacktestCandleWindowProvider candleWindowProvider = mock(
                BacktestCandleWindowProvider.class
        );
        BacktestStrategyExecutionContextFactory contextFactory = mock(
                BacktestStrategyExecutionContextFactory.class
        );
        StrategyExecutionPipeline strategyExecutionPipeline = mock(
                StrategyExecutionPipeline.class
        );
        BacktestRebalanceSchedule rebalanceSchedule = mock(
                BacktestRebalanceSchedule.class
        );
        BacktestRebalanceOrderPlanner orderPlanner = mock(
                BacktestRebalanceOrderPlanner.class
        );
        BacktestExecutionCandleResolver executionCandleResolver = mock(
                BacktestExecutionCandleResolver.class
        );
        BacktestVirtualOrderFactory virtualOrderFactory = mock(
                BacktestVirtualOrderFactory.class
        );
        BacktestPortfolioOrderExecutionService orderExecutionService = mock(
                BacktestPortfolioOrderExecutionService.class
        );
        BacktestExecutionPolicyResolver policyResolver = mock(
                BacktestExecutionPolicyResolver.class
        );
        PortfolioValuationCalculator valuationCalculator = mock(
                PortfolioValuationCalculator.class
        );
        PerformanceReportCalculator reportCalculator = mock(
                PerformanceReportCalculator.class
        );
        BacktestRun backtestRun = backtestRun();
        Asset asset = mock(Asset.class);
        Asset insufficientCandleAsset = mock(Asset.class);
        BacktestCandleWindow candleWindow = candleWindow();

        when(asset.getAssetId()).thenReturn(ASSET_ID);
        when(insufficientCandleAsset.getAssetId())
                .thenReturn(INSUFFICIENT_CANDLE_ASSET_ID);
        when(universeResolver.resolveCandidates(backtestRun))
                .thenReturn(List.of(asset, insufficientCandleAsset));
        when(universeResolver.resolve(any(BacktestRun.class), any(Instant.class)))
                .thenReturn(List.of(asset, insufficientCandleAsset));
        when(candleWindowProvider.load(any(BacktestRun.class), any()))
                .thenReturn(candleWindow);
        when(contextFactory.create(
                any(BacktestRun.class),
                any(BacktestCandleWindow.class),
                any(Instant.class),
                any()
        )).thenReturn(mock(StrategyExecutionContext.class));
        when(strategyExecutionPipeline.executeExcludingInsufficientCandleAssets(any(StrategyExecutionContext.class)))
                .thenReturn(new TargetPortfolio(
                        List.of(new TargetAssetWeight(ASSET_ID, BigDecimal.ONE)),
                        BigDecimal.ZERO
                ));
        when(rebalanceSchedule.shouldRebalance(
                any(), any(), any(), anyBoolean()
        )).thenAnswer(invocation -> invocation.getArgument(3));
        when(executionCandleResolver.resolveClosePrices(
                any(BacktestCandleWindow.class),
                any(Instant.class)
        )).thenReturn(Map.of(ASSET_ID, BigDecimal.valueOf(100)));
        when(orderPlanner.plan(any(), any(), anyMap())).thenReturn(List.of());
        when(orderExecutionService.execute(any(), any(), any()))
                .thenReturn(new BacktestOrderExecutionResult(List.of(), List.of()));
        when(policyResolver.resolve(backtestRun)).thenReturn(policy());
        when(valuationCalculator.calculateTotalAssetValue(any(), anyMap()))
                .thenReturn(BigDecimal.valueOf(1_000));
        when(reportCalculator.calculate(any(PerformanceCalculationInput.class)))
                .thenAnswer(invocation -> report(invocation.getArgument(0)));

        BacktestExecutionResult result = engine(
                universeResolver,
                candleWindowProvider,
                contextFactory,
                strategyExecutionPipeline,
                rebalanceSchedule,
                orderPlanner,
                executionCandleResolver,
                virtualOrderFactory,
                orderExecutionService,
                policyResolver,
                valuationCalculator,
                reportCalculator
        ).execute(backtestRun);

        ArgumentCaptor<PerformanceCalculationInput> inputCaptor =
                ArgumentCaptor.forClass(PerformanceCalculationInput.class);
        then(reportCalculator).should().calculate(inputCaptor.capture());

        assertThat(result.fills()).isEmpty();
        assertThat(result.assets()).containsEntry(ASSET_ID, asset)
                .containsEntry(INSUFFICIENT_CANDLE_ASSET_ID, insufficientCandleAsset);
        assertThat(result.universeAssets()).hasSize(2)
                .allSatisfy(universeAsset -> assertThat(
                        universeAsset.getId().getRebalanceAt()
                ).isEqualTo(Instant.parse("2026-01-02T01:00:00Z")));
        assertThat(result.universeAssets()).anySatisfy(universeAsset -> {
            assertThat(universeAsset.getId().getAssetId()).isEqualTo(ASSET_ID);
            assertThat(universeAsset.isSelected()).isTrue();
        });
        assertThat(result.universeAssets()).anySatisfy(universeAsset -> {
            assertThat(universeAsset.getId().getAssetId())
                    .isEqualTo(INSUFFICIENT_CANDLE_ASSET_ID);
            assertThat(universeAsset.isSelected()).isFalse();
        });
        assertThat(inputCaptor.getValue().equitySnapshots()).hasSize(3);
        assertThat(inputCaptor.getValue().fills()).isEmpty();
    }

    private DefaultBacktestEngine engine(
            BacktestUniverseResolver universeResolver,
            BacktestCandleWindowProvider candleWindowProvider,
            BacktestStrategyExecutionContextFactory contextFactory,
            StrategyExecutionPipeline strategyExecutionPipeline,
            BacktestRebalanceSchedule rebalanceSchedule,
            BacktestRebalanceOrderPlanner orderPlanner,
            BacktestExecutionCandleResolver executionCandleResolver,
            BacktestVirtualOrderFactory virtualOrderFactory,
            BacktestPortfolioOrderExecutionService orderExecutionService,
            BacktestExecutionPolicyResolver policyResolver,
            PortfolioValuationCalculator valuationCalculator,
            PerformanceReportCalculator reportCalculator
    ) {
        return new DefaultBacktestEngine(
                universeResolver,
                candleWindowProvider,
                contextFactory,
                strategyExecutionPipeline,
                rebalanceSchedule,
                orderPlanner,
                executionCandleResolver,
                virtualOrderFactory,
                orderExecutionService,
                policyResolver,
                valuationCalculator,
                reportCalculator,
                new CumulativeReturnCalculator(),
                new DrawdownRateCalculator()
        );
    }

    private BacktestRun backtestRun() {
        BacktestRun backtestRun = mock(BacktestRun.class);
        when(backtestRun.getBacktestId()).thenReturn(BACKTEST_ID);
        when(backtestRun.getPeriodStart()).thenReturn(PERIOD_START);
        when(backtestRun.getPeriodEnd()).thenReturn(PERIOD_END);
        when(backtestRun.getTimeframe()).thenReturn(Timeframe.ONE_HOUR);
        when(backtestRun.getInitialCapital()).thenReturn(BigDecimal.valueOf(1_000));
        when(backtestRun.getRebalanceInterval()).thenReturn(RebalancePeriod.DAILY);
        return backtestRun;
    }

    private BacktestCandleWindow candleWindow() {
        return new BacktestCandleWindow(
                Timeframe.ONE_HOUR,
                PERIOD_START,
                PERIOD_END,
                Map.of(ASSET_ID, List.of(
                        candle(PERIOD_START),
                        candle(Instant.parse("2026-01-02T01:00:00Z")),
                        candle(PERIOD_END)
                ))
        );
    }

    private MinuteCandle candle(Instant openTime) {
        return MinuteCandleFixture.minuteCandle(
                ASSET_ID,
                Timeframe.ONE_HOUR.getIntervalMinutes(),
                openTime,
                BigDecimal.valueOf(100),
                BigDecimal.ONE
        );
    }

    private VirtualExecutionPolicy policy() {
        return new VirtualExecutionPolicy(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE
        );
    }

    private PerformanceReport report(PerformanceCalculationInput input) {
        return new PerformanceReport(
                input.equitySnapshots().getLast().totalAssetValue(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                new TradeStatistics(0, BigDecimal.ZERO, BigDecimal.ZERO),
                new CostComparison(
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO
                ),
                List.of(),
                input.equitySnapshots()
        );
    }
}
