package com.ssafy.a107.trading.simulation.service;

import com.ssafy.a107.asset.domain.Asset;
import com.ssafy.a107.asset.domain.Exchange;
import com.ssafy.a107.asset.service.CollectedAssetReadService;
import com.ssafy.a107.candle.domain.MinuteCandle;
import com.ssafy.a107.candle.domain.MinuteCandleId;
import com.ssafy.a107.member.domain.Member;
import com.ssafy.a107.strategy.domain.Strategy;
import com.ssafy.a107.strategy.domain.dsl.ComparisonOperator;
import com.ssafy.a107.strategy.domain.dsl.ConditionFilterNode;
import com.ssafy.a107.strategy.domain.dsl.FactorOperand;
import com.ssafy.a107.strategy.domain.dsl.FactorType;
import com.ssafy.a107.strategy.domain.dsl.Ranking;
import com.ssafy.a107.strategy.domain.dsl.RankingDirection;
import com.ssafy.a107.strategy.domain.dsl.Rebalance;
import com.ssafy.a107.strategy.domain.dsl.RebalancePeriod;
import com.ssafy.a107.strategy.domain.dsl.Selection;
import com.ssafy.a107.strategy.domain.dsl.StrategyDsl;
import com.ssafy.a107.strategy.domain.dsl.Timeframe;
import com.ssafy.a107.strategy.domain.dsl.ValueOperand;
import com.ssafy.a107.strategy.engine.StrategyExecutionPipeline;
import com.ssafy.a107.strategy.engine.factor.Ta4jFactorCalculator;
import com.ssafy.a107.strategy.engine.filter.FilterEvaluator;
import com.ssafy.a107.strategy.engine.portfolio.TargetPortfolioEvaluator;
import com.ssafy.a107.strategy.engine.ranking.RankingEvaluator;
import com.ssafy.a107.strategy.engine.selection.SelectionEvaluator;
import com.ssafy.a107.trading.backtest.domain.ExecutionPriceRule;
import com.ssafy.a107.trading.backtest.domain.PositionSide;
import com.ssafy.a107.trading.backtest.domain.TradeSide;
import com.ssafy.a107.trading.backtest.engine.BacktestRebalanceSchedule;
import com.ssafy.a107.trading.backtest.engine.execution.BacktestExecutionPolicyProperties;
import com.ssafy.a107.trading.backtest.engine.execution.KrwPriceTickSizeResolver;
import com.ssafy.a107.trading.backtest.engine.execution.OrderPrecisionCalculator;
import com.ssafy.a107.trading.backtest.engine.execution.SlippageAmountCalculator;
import com.ssafy.a107.trading.backtest.engine.execution.SlippagePriceCalculator;
import com.ssafy.a107.trading.backtest.engine.execution.TradeFeeCalculator;
import com.ssafy.a107.trading.backtest.engine.execution.VirtualExecutionCalculator;
import com.ssafy.a107.trading.backtest.engine.marketdata.BacktestWarmupCandleCountResolver;
import com.ssafy.a107.trading.backtest.engine.marketdata.HistoricalCandleProvider;
import com.ssafy.a107.trading.backtest.engine.portfolio.BacktestPortfolioOrderExecutionService;
import com.ssafy.a107.trading.backtest.engine.portfolio.BacktestRebalanceOrderPlanner;
import com.ssafy.a107.trading.backtest.engine.portfolio.PortfolioValuationCalculator;
import com.ssafy.a107.trading.simulation.domain.SimulationPosition;
import com.ssafy.a107.trading.simulation.domain.SimulationRun;
import com.ssafy.a107.trading.simulation.domain.SimulationTrade;
import com.ssafy.a107.trading.simulation.repository.SimulationEquityRepository;
import com.ssafy.a107.trading.simulation.repository.SimulationPositionRepository;
import com.ssafy.a107.trading.simulation.repository.SimulationResultRepository;
import com.ssafy.a107.trading.simulation.repository.SimulationRunRepository;
import com.ssafy.a107.trading.simulation.repository.SimulationTradeRepository;
import com.ssafy.a107.trading.simulation.repository.SimulationUniverseAssetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class SimulationCandleReadinessTest {

    private static final Instant EXECUTION_AT = Instant.parse("2026-09-18T00:00:00Z");
    private static final Instant EVALUATION_AT = EXECUTION_AT.minus(Duration.ofDays(1));
    private static final BigDecimal INITIAL_CAPITAL = new BigDecimal("3000000");
    private final UUID simulationId = UUID.randomUUID();
    private final SimulationRunRepository runs = mock(SimulationRunRepository.class);
    private final SimulationPositionRepository positions = mock(SimulationPositionRepository.class);
    private final SimulationTradeRepository trades = mock(SimulationTradeRepository.class);
    private final SimulationEquityRepository equities = mock(SimulationEquityRepository.class);
    private final SimulationResultRepository results = mock(SimulationResultRepository.class);
    private final SimulationUniverseAssetRepository universeRecords = mock(SimulationUniverseAssetRepository.class);
    private final CollectedAssetReadService collectedAssets = mock(CollectedAssetReadService.class);
    private final HistoricalCandleProvider history = mock(HistoricalCandleProvider.class);
    private final SimulationMarketPriceCache prices = mock(SimulationMarketPriceCache.class);
    private final List<Asset> universe = new ArrayList<>();
    private final Map<UUID, List<MinuteCandle>> candles = new HashMap<>();
    private final PortfolioValuationCalculator valuation = new PortfolioValuationCalculator();
    private final Strategy strategy = mock(Strategy.class);
    private SimulationRun run;
    private SimulationCandleExecutionService service;

    @BeforeEach
    void setUp() {
        given(strategy.getStrategyDsl()).willReturn(strategyDsl("100"));
        run = new SimulationRun(mock(Member.class), strategy, INITIAL_CAPITAL,
                new BigDecimal("0.0005"), new BigDecimal("5"), ExecutionPriceRule.NEXT_CANDLE_OPEN, "test");
        ReflectionTestUtils.setField(run, "simulationId", simulationId);
        run.start(EXECUTION_AT.minus(Duration.ofDays(2)));
        given(runs.findBySimulationIdForUpdate(simulationId)).willReturn(Optional.of(run));
        given(collectedAssets.findAvailableAt(any(), any(), any())).willReturn(universe);
        given(trades.sumFeeAmountBySimulationId(simulationId)).willReturn(BigDecimal.ZERO);
        given(history.load(any(), any(), any(), any())).willAnswer(invocation -> {
            Collection<UUID> assetIds = invocation.getArgument(0);
            Instant from = invocation.getArgument(2);
            Instant to = invocation.getArgument(3);
            Map<UUID, List<MinuteCandle>> loaded = new HashMap<>();
            for (UUID assetId : assetIds) {
                loaded.put(assetId, candles.getOrDefault(assetId, List.of()).stream()
                        .filter(candle -> !candle.getId().getOpenTime().isBefore(from)
                                && candle.getId().getOpenTime().isBefore(to))
                        .toList());
            }
            return loaded;
        });
        given(prices.snapshotAt(any(), anyInt(), any())).willAnswer(invocation -> {
            Collection<UUID> assetIds = invocation.getArgument(0);
            Map<UUID, BigDecimal> snapshot = new HashMap<>();
            assetIds.forEach(assetId -> snapshot.put(assetId, new BigDecimal("121")));
            return Optional.of(snapshot);
        });
        service = new SimulationCandleExecutionService(runs, positions, trades, equities, results, universeRecords,
                collectedAssets, history, new BacktestWarmupCandleCountResolver(),
                new StrategyExecutionPipeline(new Ta4jFactorCalculator(), new FilterEvaluator(),
                        new RankingEvaluator(), new SelectionEvaluator(), new TargetPortfolioEvaluator()),
                new BacktestRebalanceOrderPlanner(valuation), new BacktestRebalanceSchedule(),
                new BacktestPortfolioOrderExecutionService(new VirtualExecutionCalculator(
                        new SlippagePriceCalculator(), new OrderPrecisionCalculator(new KrwPriceTickSizeResolver()),
                        new TradeFeeCalculator(), new SlippageAmountCalculator())),
                valuation, new BacktestExecutionPolicyProperties(new BigDecimal("5000"), new BigDecimal("0.00000001")),
                prices, mock(PlatformTransactionManager.class), mock(SimulationExecutionFailureService.class));
    }

    @Test
    @DisplayName("최신 확정 일봉이 없으면 완료 기록 없이 재시도를 요청한다")
    void waitForLatestCandle() {
        addAsset(null, 20);

        boolean retry = process();

        assertThat(retry).isTrue();
        assertUnchanged();
    }

    @Test
    @DisplayName("확정 일봉이 늦게 도착하면 같은 실행에서 한 번만 매수한다")
    void buyOnceAfterDelayedCandleArrives() {
        Asset asset = addAsset(null, 20);
        process();
        candles.get(asset.getAssetId()).add(candle(asset.getAssetId(), EVALUATION_AT, 120));

        boolean retry = process();
        process();

        assertThat(retry).isFalse();
        assertThat(run.getLastRebalanceAt()).isEqualTo(EXECUTION_AT);
        assertThat(run.getCashBalance()).isLessThan(INITIAL_CAPITAL);
        ArgumentCaptor<SimulationTrade> trade = ArgumentCaptor.forClass(SimulationTrade.class);
        verify(trades, times(1)).save(trade.capture());
        assertThat(trade.getValue().getSide()).isEqualTo(TradeSide.BUY);
    }

    @Test
    @DisplayName("일부 종목만 준비되어도 불완전한 후보군으로 매수하지 않는다")
    void waitForAllMatureAssets() {
        addAsset(null, 21);
        addAsset(EXECUTION_AT.minus(Duration.ofDays(100)), 20);

        assertThat(process()).isTrue();
        assertUnchanged();
    }

    @Test
    @DisplayName("과거 구간에 공백이 있으면 최신 봉만으로 준비 완료로 처리하지 않는다")
    void waitForMissingHistoricalCandle() {
        Asset asset = addAsset(null, 21);
        candles.get(asset.getAssetId()).remove(5);

        assertThat(process()).isTrue();
        assertUnchanged();
    }

    @Test
    @DisplayName("데이터 부족으로 후보가 비어도 기존 보유를 청산하지 않는다")
    void preserveExistingPositionWhileWaiting() {
        Asset asset = addAsset(null, 21);
        candles.get(asset.getAssetId()).remove(5);
        given(positions.findAllBySimulationRunSimulationIdOrderByIdAssetIdAsc(simulationId)).willReturn(List.of(
                new SimulationPosition(run, asset, PositionSide.LONG, BigDecimal.TEN, new BigDecimal("100"), EVALUATION_AT)
        ));

        assertThat(process()).isTrue();
        assertUnchanged();
        verify(positions, times(0)).deleteAllBySimulationRunSimulationId(any());
    }

    @Test
    @DisplayName("데이터가 충분하지만 필터를 통과하지 못하면 정상 현금 보유로 완료한다")
    void keepCashForLegitimateEmptySelection() {
        addAsset(null, 21);
        given(strategy.getStrategyDsl()).willReturn(strategyDsl("0"));

        assertThat(process()).isFalse();

        assertThat(run.getLastRebalanceAt()).isEqualTo(EXECUTION_AT);
        assertThat(run.getCashBalance()).isEqualByComparingTo(INITIAL_CAPITAL);
        verify(trades, times(0)).save(any());
        verify(equities).save(any());
    }

    @Test
    @DisplayName("워밍업 시작 이후 상장된 종목은 제외하고 준비된 종목을 매수한다")
    void excludeKnownNewListing() {
        addAsset(null, 21);
        addAsset(EXECUTION_AT.minus(Duration.ofDays(3)), 0);

        assertThat(process()).isFalse();

        assertThat(run.getCashBalance()).isLessThan(INITIAL_CAPITAL);
        verify(trades).save(any());
    }

    @Test
    @DisplayName("진행 중 봉을 포함해도 부족한 확정 봉을 대신하지 않는다")
    void rejectCurrentOpenCandleAsWarmup() {
        Asset asset = addAsset(null, 20);
        candles.get(asset.getAssetId()).add(candle(asset.getAssetId(), EXECUTION_AT, 121));

        assertThat(process()).isTrue();
        assertUnchanged();
    }

    @Test
    @DisplayName("요청한 기존 종목이 조회 결과에서 빠져도 준비 완료로 처리하지 않는다")
    void waitForAssetMissingFromLoadedMap() {
        addAsset(null, 21);
        willReturn(Map.of()).given(history).load(any(), any(), any(), any());

        assertThat(process()).isTrue();
        assertUnchanged();
    }

    @Test
    @DisplayName("수집 중단 종목이 있어도 정상 종목의 확정 봉을 기다렸다가 한 번만 매수한다")
    void buyAfterDelayedCandleWithStaleAssetInUniverse() {
        Asset healthy = addAsset(null, 20);
        // 운영 STORJ와 동일하게 8월 28일부터 9월 14일까지 18개만 남아 있다.
        addAsset(null, 18);

        assertThat(process()).isTrue();
        assertUnchanged();
        candles.get(healthy.getAssetId()).add(candle(healthy.getAssetId(), EVALUATION_AT, 120));
        assertThat(process()).isFalse();
        assertThat(process()).isFalse();

        ArgumentCaptor<SimulationTrade> trade = ArgumentCaptor.forClass(SimulationTrade.class);
        verify(trades).save(trade.capture());
        assertThat(trade.getValue().getAsset().getAssetId()).isEqualTo(healthy.getAssetId());
        assertThat(trade.getValue().getSide()).isEqualTo(TradeSide.BUY);
        assertThat(run.getCashBalance()).isLessThan(INITIAL_CAPITAL);
        assertThat(run.getLastRebalanceAt()).isEqualTo(EXECUTION_AT);
    }

    @Test
    @DisplayName("모든 종목의 수집이 멈췄으면 현금 보유로 완료하지 않는다")
    void waitWhenAllAssetsHaveStaleHistory() {
        addAsset(null, 18);
        addAsset(null, 0);

        assertThat(process()).isTrue();
        assertUnchanged();
    }

    @Test
    @DisplayName("일부 후보의 과거 이력이 부족하면 충분한 이력을 가진 종목으로 선정한다")
    void excludeIncompleteHistoricalSeries() {
        Asset healthy = addAsset(null, 21);
        Asset incomplete = addAsset(null, 21);
        candles.get(incomplete.getAssetId()).remove(5);

        assertThat(process()).isFalse();

        ArgumentCaptor<SimulationTrade> trade = ArgumentCaptor.forClass(SimulationTrade.class);
        verify(trades).save(trade.capture());
        assertThat(trade.getValue().getAsset().getAssetId()).isEqualTo(healthy.getAssetId());
    }

    @Test
    @DisplayName("다른 후보가 준비돼도 보유 종목의 이력이 부족하면 임의로 청산하지 않는다")
    void protectIncompleteHoldingWhenOtherAssetsAreReady() {
        addAsset(null, 21);
        Asset held = addAsset(null, 21);
        candles.get(held.getAssetId()).remove(5);
        given(positions.findAllBySimulationRunSimulationIdOrderByIdAssetIdAsc(simulationId)).willReturn(List.of(
                new SimulationPosition(run, held, PositionSide.LONG, BigDecimal.TEN, new BigDecimal("100"), EVALUATION_AT)
        ));

        assertThat(process()).isTrue();
        assertUnchanged();
        verify(positions, times(0)).deleteAllBySimulationRunSimulationId(any());
    }

    private boolean process() {
        return service.process(simulationId, EVALUATION_AT, EXECUTION_AT);
    }

    private void assertUnchanged() {
        assertThat(run.getLastCandleAt()).isNull();
        assertThat(run.getLastRebalanceAt()).isNull();
        assertThat(run.getCashBalance()).isEqualByComparingTo(INITIAL_CAPITAL);
        verifyNoInteractions(trades, equities, results, universeRecords);
    }

    private Asset addAsset(Instant listedAt, int candleCount) {
        Asset asset = new Asset(Exchange.UPBIT, "KRW-TEST", "TEST", "KRW", "테스트", "Test", listedAt);
        UUID assetId = UUID.randomUUID();
        ReflectionTestUtils.setField(asset, "assetId", assetId);
        universe.add(asset);
        List<MinuteCandle> series = new ArrayList<>();
        for (int index = 0; index < candleCount; index++) {
            series.add(candle(assetId, EXECUTION_AT.minus(Duration.ofDays(21 - index)), 100 + index));
        }
        candles.put(assetId, series);
        return asset;
    }

    private MinuteCandle candle(UUID assetId, Instant at, int value) {
        BigDecimal price = BigDecimal.valueOf(value);
        return MinuteCandle.detached(new MinuteCandleId(assetId, 1440, at), price, price, price, price,
                BigDecimal.ONE, price, at.plus(Duration.ofDays(1)).minusMillis(1));
    }

    private StrategyDsl strategyDsl(String maximumVolatility) {
        return new StrategyDsl(Timeframe.ONE_DAY,
                new ConditionFilterNode(new FactorOperand(FactorType.VOLATILITY, 20), ComparisonOperator.LT,
                        new ValueOperand(new BigDecimal(maximumVolatility))),
                new Ranking(FactorType.RETURN, 20, RankingDirection.DESC), new Selection(5),
                new Rebalance(RebalancePeriod.DAILY));
    }
}
