package com.ssafy.a107.trading.simulation.service;

import com.ssafy.a107.asset.service.CollectedAssetReadService;
import com.ssafy.a107.asset.domain.Asset;
import com.ssafy.a107.candle.domain.MinuteCandle;
import com.ssafy.a107.candle.domain.MinuteCandleId;
import com.ssafy.a107.asset.domain.Exchange;
import com.ssafy.a107.realtime.domain.LiveMinuteCandle;
import com.ssafy.a107.strategy.engine.StrategyExecutionContext;
import com.ssafy.a107.strategy.engine.StrategyExecutionPipeline;
import com.ssafy.a107.strategy.engine.portfolio.TargetAssetWeight;
import com.ssafy.a107.strategy.engine.portfolio.TargetPortfolio;
import com.ssafy.a107.strategy.domain.Strategy;
import com.ssafy.a107.strategy.domain.dsl.ComparisonOperator;
import com.ssafy.a107.strategy.domain.dsl.ConditionFilterNode;
import com.ssafy.a107.strategy.domain.dsl.FactorOperand;
import com.ssafy.a107.strategy.domain.dsl.FactorType;
import com.ssafy.a107.strategy.domain.dsl.RebalancePeriod;
import com.ssafy.a107.strategy.domain.dsl.Ranking;
import com.ssafy.a107.strategy.domain.dsl.RankingDirection;
import com.ssafy.a107.strategy.domain.dsl.Rebalance;
import com.ssafy.a107.strategy.domain.dsl.Selection;
import com.ssafy.a107.strategy.domain.dsl.StrategyDsl;
import com.ssafy.a107.strategy.domain.dsl.Timeframe;
import com.ssafy.a107.strategy.domain.dsl.ValueOperand;
import com.ssafy.a107.trading.backtest.engine.BacktestRebalanceSchedule;
import com.ssafy.a107.trading.backtest.engine.execution.BacktestExecutionPolicyProperties;
import com.ssafy.a107.trading.backtest.engine.marketdata.BacktestWarmupCandleCountResolver;
import com.ssafy.a107.trading.backtest.engine.marketdata.HistoricalCandleProvider;
import com.ssafy.a107.trading.backtest.engine.portfolio.BacktestOrderExecutionResult;
import com.ssafy.a107.trading.backtest.engine.portfolio.BacktestPortfolioOrderExecutionService;
import com.ssafy.a107.trading.backtest.engine.portfolio.BacktestRebalanceOrder;
import com.ssafy.a107.trading.backtest.engine.portfolio.BacktestRebalanceOrderPlanner;
import com.ssafy.a107.trading.backtest.engine.portfolio.PortfolioValuationCalculator;
import com.ssafy.a107.trading.backtest.engine.execution.VirtualOrder;
import com.ssafy.a107.trading.backtest.domain.PositionSide;
import com.ssafy.a107.trading.backtest.domain.TradeSide;
import com.ssafy.a107.trading.simulation.domain.SimulationRunStatus;
import com.ssafy.a107.trading.simulation.domain.SimulationRun;
import com.ssafy.a107.trading.simulation.repository.SimulationEquityRepository;
import com.ssafy.a107.trading.simulation.repository.SimulationPositionRepository;
import com.ssafy.a107.trading.simulation.repository.SimulationResultRepository;
import com.ssafy.a107.trading.simulation.repository.SimulationRunRepository;
import com.ssafy.a107.trading.simulation.repository.SimulationTradeRepository;
import com.ssafy.a107.trading.simulation.repository.SimulationUniverseAssetRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

class SimulationCandleExecutionServiceTest {

    private final SimulationRunRepository simulationRunRepository = mock(SimulationRunRepository.class);
    private final SimulationPositionRepository simulationPositionRepository = mock(SimulationPositionRepository.class);
    private final SimulationTradeRepository simulationTradeRepository = mock(SimulationTradeRepository.class);
    private final SimulationEquityRepository simulationEquityRepository = mock(SimulationEquityRepository.class);
    private final SimulationResultRepository simulationResultRepository = mock(SimulationResultRepository.class);
    private final SimulationUniverseAssetRepository simulationUniverseAssetRepository = mock(SimulationUniverseAssetRepository.class);
    private final CollectedAssetReadService collectedAssetReadService = mock(CollectedAssetReadService.class);
    private final HistoricalCandleProvider historicalCandleProvider = mock(HistoricalCandleProvider.class);
    private final BacktestWarmupCandleCountResolver warmupCandleCountResolver = mock(BacktestWarmupCandleCountResolver.class);
    private final StrategyExecutionPipeline strategyExecutionPipeline = mock(StrategyExecutionPipeline.class);
    private final BacktestRebalanceSchedule rebalanceSchedule = mock(BacktestRebalanceSchedule.class);
    private final BacktestRebalanceOrderPlanner rebalanceOrderPlanner = mock(BacktestRebalanceOrderPlanner.class);
    private final BacktestPortfolioOrderExecutionService portfolioOrderExecutionService = mock(BacktestPortfolioOrderExecutionService.class);
    private final PortfolioValuationCalculator portfolioValuationCalculator = mock(PortfolioValuationCalculator.class);
    private final BacktestExecutionPolicyProperties executionPolicyProperties = mock(BacktestExecutionPolicyProperties.class);
    private final SimulationMarketPriceCache marketPriceCache = mock(SimulationMarketPriceCache.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final SimulationExecutionFailureService failureService = mock(SimulationExecutionFailureService.class);
    private final SimulationCandleExecutionService executionService = executionService();

    @DisplayName("모의투자 시간프레임이 아닌 Candle은 실행 조회 없이 가격만 갱신한다")
    @Test
    void ignoreUnsupportedSimulationInterval() {
        LiveMinuteCandle candle = candle(1);

        executionService.handle(candle);

        then(marketPriceCache).should().update(candle);
        then(simulationRunRepository).shouldHaveNoMoreInteractions();
    }

    @DisplayName("일시적인 DB 잠금 오류는 실행을 실패 처리하지 않고 재시도한다")
    @Test
    void retryTransientTransactionFailureWithoutFailingSimulation() {
        UUID simulationId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        given(simulationRunRepository.findSimulationIdsByStatusAndTimeframe(
                eq(SimulationRunStatus.RUNNING), any(), any())).willReturn(List.of(simulationId));
        given(transactionManager.getTransaction(any())).willThrow(new CannotAcquireLockException("lock"));

        executionService.handle(candle(60));

        then(transactionManager).should(org.mockito.Mockito.times(3)).getTransaction(any());
        then(failureService).shouldHaveNoInteractions();
    }

    @DisplayName("실행 대상 조회의 일시적인 DB 오류도 재시도한다")
    @Test
    void retryTransientRunningSimulationLookup() {
        given(simulationRunRepository.findSimulationIdsByStatusAndTimeframe(
                eq(SimulationRunStatus.RUNNING), any(), any())).willThrow(new CannotAcquireLockException("lock"));

        executionService.handle(candle(60));

        then(simulationRunRepository).should(org.mockito.Mockito.times(3))
                .findSimulationIdsByStatusAndTimeframe(eq(SimulationRunStatus.RUNNING), any(), any());
        then(failureService).shouldHaveNoInteractions();
    }

    @DisplayName("모의투자는 이전 Candle 종가를 평가하고 다음 Candle 시점에 실행한다")
    @Test
    void evaluatePreviousCandleAndExecuteAtNextCandleOpen() {
        UUID simulationId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        java.time.Instant evaluationCandleOpenAt = java.time.Instant.parse("2026-09-14T09:00:00Z");
        java.time.Instant executionAt = java.time.Instant.parse("2026-09-14T10:00:00Z");
        SimulationRun simulationRun = mock(SimulationRun.class);
        given(simulationRunRepository.findBySimulationIdForUpdate(simulationId)).willReturn(java.util.Optional.of(simulationRun));
        given(simulationRun.getStatus()).willReturn(SimulationRunStatus.RUNNING);
        given(simulationRun.getStartedAt()).willReturn(evaluationCandleOpenAt);
        given(simulationRun.getQuoteCurrency()).willReturn("KRW");
        given(simulationRun.getRebalanceInterval()).willReturn(RebalancePeriod.DAILY);
        given(simulationRun.getTimeframe()).willReturn(Timeframe.ONE_HOUR);
        given(simulationPositionRepository.findAllBySimulationRunSimulationIdOrderByIdAssetIdAsc(simulationId))
                .willReturn(List.of());
        given(rebalanceSchedule.shouldRebalance(RebalancePeriod.DAILY, Timeframe.ONE_HOUR,
                evaluationCandleOpenAt, true)).willReturn(false);

        executionService.process(simulationId, evaluationCandleOpenAt, executionAt);

        then(collectedAssetReadService).should().findAvailableAt(Exchange.UPBIT, "KRW", evaluationCandleOpenAt);
        then(rebalanceSchedule).should().shouldRebalance(
                RebalancePeriod.DAILY, Timeframe.ONE_HOUR, evaluationCandleOpenAt, true
        );
        then(simulationRun).should().beginCandle(executionAt);
    }

    @DisplayName("모의투자는 완료된 이전 Candle 종가로 평가하고 현재 Candle 시가로 체결한다")
    @Test
    void evaluateCompletedCandleAndExecuteAtCurrentCandleOpen() {
        UUID simulationId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID assetId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        Instant evaluationCandleOpenAt = Instant.parse("2026-09-14T09:00:00Z");
        Instant executionAt = Instant.parse("2026-09-14T10:00:00Z");
        SimulationRun simulationRun = mock(SimulationRun.class);
        Strategy strategy = mock(Strategy.class);
        Asset asset = mock(Asset.class);
        StrategyDsl strategyDsl = strategyDsl();
        MinuteCandle completedCandle = candle(
                assetId,
                evaluationCandleOpenAt,
                "100",
                "110"
        );
        MinuteCandle currentCandle = candle(
                assetId,
                executionAt,
                "120",
                "130"
        );

        given(simulationRunRepository.findBySimulationIdForUpdate(simulationId))
                .willReturn(Optional.of(simulationRun));
        given(simulationRun.getSimulationId()).willReturn(simulationId);
        given(simulationRun.getStatus()).willReturn(SimulationRunStatus.RUNNING);
        given(simulationRun.getStartedAt()).willReturn(evaluationCandleOpenAt);
        given(simulationRun.getQuoteCurrency()).willReturn("KRW");
        given(simulationRun.getRebalanceInterval()).willReturn(RebalancePeriod.DAILY);
        given(simulationRun.getTimeframe()).willReturn(Timeframe.ONE_HOUR);
        given(simulationRun.getStrategy()).willReturn(strategy);
        given(simulationRun.getCashBalance()).willReturn(BigDecimal.valueOf(1_000));
        given(simulationRun.getInitialCapital()).willReturn(BigDecimal.valueOf(1_000));
        given(simulationRun.getFeeRate()).willReturn(BigDecimal.ZERO);
        given(simulationRun.getSlippageBps()).willReturn(BigDecimal.ZERO);
        given(simulationRun.beginCandle(executionAt)).willReturn(true);
        given(strategy.getStrategyDsl()).willReturn(strategyDsl);
        given(asset.getAssetId()).willReturn(assetId);
        given(collectedAssetReadService.findAvailableAt(Exchange.UPBIT, "KRW", evaluationCandleOpenAt))
                .willReturn(List.of(asset));
        given(simulationPositionRepository.findAllBySimulationRunSimulationIdOrderByIdAssetIdAsc(simulationId))
                .willReturn(List.of());
        given(rebalanceSchedule.shouldRebalance(
                RebalancePeriod.DAILY,
                Timeframe.ONE_HOUR,
                evaluationCandleOpenAt,
                true
        )).willReturn(true);
        given(warmupCandleCountResolver.resolve(strategyDsl)).willReturn(1);
        given(historicalCandleProvider.load(
                any(),
                eq(Timeframe.ONE_HOUR),
                eq(evaluationCandleOpenAt),
                eq(executionAt)
        )).willReturn(Map.of(assetId, List.of(
                completedCandle,
                currentCandle
        )));
        given(strategyExecutionPipeline.execute(any(StrategyExecutionContext.class)))
                .willReturn(new TargetPortfolio(
                        List.of(new TargetAssetWeight(assetId, BigDecimal.ONE)),
                        BigDecimal.ZERO
                ));
        given(marketPriceCache.snapshotAt(any(), eq(60), eq(executionAt)))
                .willReturn(Optional.of(Map.of(assetId, BigDecimal.valueOf(120))));
        given(rebalanceOrderPlanner.plan(any(), any(), any()))
                .willReturn(List.of(new BacktestRebalanceOrder(
                        assetId,
                        TradeSide.BUY,
                        BigDecimal.valueOf(100)
                )));
        given(portfolioOrderExecutionService.execute(any(), any(), any()))
                .willReturn(new BacktestOrderExecutionResult(List.of(), List.of()));
        given(executionPolicyProperties.minimumOrderAmount()).willReturn(BigDecimal.ONE);
        given(executionPolicyProperties.priceTickSize()).willReturn(BigDecimal.ONE);
        given(executionPolicyProperties.quantityStepSize()).willReturn(BigDecimal.ONE);
        given(portfolioValuationCalculator.calculateTotalAssetValue(any(), any()))
                .willReturn(BigDecimal.valueOf(1_000));
        given(simulationEquityRepository.findMaximumTotalAssetValueBySimulationId(simulationId))
                .willReturn(null);
        given(simulationTradeRepository.countBySimulationRunSimulationId(simulationId))
                .willReturn(0L);
        given(simulationTradeRepository.sumFeeAmountBySimulationId(simulationId))
                .willReturn(BigDecimal.ZERO);
        given(simulationResultRepository.findById(simulationId)).willReturn(Optional.empty());

        executionService.process(simulationId, evaluationCandleOpenAt, executionAt);

        ArgumentCaptor<StrategyExecutionContext> contextCaptor = ArgumentCaptor.forClass(
                StrategyExecutionContext.class
        );
        then(strategyExecutionPipeline).should().execute(contextCaptor.capture());
        then(rebalanceOrderPlanner).should().plan(
                any(),
                any(),
                eq(Map.of(assetId, BigDecimal.valueOf(110)))
        );
        then(portfolioOrderExecutionService).should().execute(
                any(),
                eq(List.of(new VirtualOrder(
                        assetId,
                        PositionSide.LONG,
                        TradeSide.BUY,
                        executionAt,
                        executionAt,
                        BigDecimal.valueOf(120),
                        BigDecimal.valueOf(100)
                ))),
                any()
        );

        StrategyExecutionContext context = contextCaptor.getValue();
        assertThat(context.evaluationAt()).isEqualTo(executionAt);
        assertThat(context.candlesByAssetId().get(assetId))
                .extracting(candle -> candle.getId().getOpenTime())
                .containsExactly(evaluationCandleOpenAt);
    }

    @DisplayName("재시작 뒤 누락한 Candle은 다음 실시간 Candle보다 먼저 복구한다")
    @Test
    void recoverMissedCandleBeforeCurrentLiveCandle() {
        UUID simulationId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        Instant lastCandleAt = Instant.parse("2026-09-14T00:00:00Z");
        Instant currentExecutionAt = Instant.parse("2026-09-14T02:00:00Z");
        SimulationRun simulationRun = mock(SimulationRun.class);
        given(simulationRunRepository.findBySimulationIdForUpdate(simulationId)).willReturn(java.util.Optional.of(simulationRun));
        given(simulationRun.getSimulationId()).willReturn(simulationId);
        given(simulationRun.getStatus()).willReturn(SimulationRunStatus.RUNNING);
        given(simulationRun.getStartedAt()).willReturn(lastCandleAt);
        given(simulationRun.getLastCandleAt()).willReturn(lastCandleAt);
        given(simulationRun.getLastRebalanceAt()).willReturn(lastCandleAt);
        given(simulationRun.getQuoteCurrency()).willReturn("KRW");
        given(simulationRun.getRebalanceInterval()).willReturn(RebalancePeriod.DAILY);
        given(simulationRun.getTimeframe()).willReturn(Timeframe.ONE_HOUR);
        given(simulationRun.beginCandle(org.mockito.ArgumentMatchers.any())).willReturn(true);
        given(collectedAssetReadService.findAvailableAt(any(), any(), any())).willReturn(List.of());
        given(simulationPositionRepository.findAllBySimulationRunSimulationIdOrderByIdAssetIdAsc(simulationId))
                .willReturn(List.of());
        given(rebalanceSchedule.shouldRebalance(any(), any(), any(), anyBoolean())).willReturn(false);

        executionService.process(simulationId, currentExecutionAt.minusSeconds(3600), currentExecutionAt);

        InOrder inOrder = org.mockito.Mockito.inOrder(rebalanceSchedule, simulationRun);
        inOrder.verify(rebalanceSchedule).shouldRebalance(
                RebalancePeriod.DAILY, Timeframe.ONE_HOUR, lastCandleAt, false
        );
        inOrder.verify(simulationRun).beginCandle(lastCandleAt.plusSeconds(3600));
        inOrder.verify(rebalanceSchedule).shouldRebalance(
                RebalancePeriod.DAILY, Timeframe.ONE_HOUR, currentExecutionAt.minusSeconds(3600), false
        );
        inOrder.verify(simulationRun).beginCandle(currentExecutionAt);
    }

    @DisplayName("누락된 리밸런싱 Candle은 확정 Candle 시가로 복구한다")
    @Test
    void recoverMissedRebalanceUsingHistoricalOpenPrice() {
        UUID simulationId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID assetId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        Instant lastCandleAt = Instant.parse("2026-09-14T22:00:00Z");
        Instant recoveryExecutionAt = Instant.parse("2026-09-15T00:00:00Z");
        Instant currentExecutionAt = Instant.parse("2026-09-15T01:00:00Z");
        SimulationRun simulationRun = mock(SimulationRun.class);
        Strategy strategy = mock(Strategy.class);
        StrategyDsl strategyDsl = strategyDsl();
        Asset asset = mock(Asset.class);
        given(simulationRunRepository.findBySimulationIdForUpdate(simulationId)).willReturn(java.util.Optional.of(simulationRun));
        given(simulationRun.getSimulationId()).willReturn(simulationId);
        given(simulationRun.getStatus()).willReturn(SimulationRunStatus.RUNNING);
        given(simulationRun.getStartedAt()).willReturn(lastCandleAt);
        given(simulationRun.getLastCandleAt()).willReturn(lastCandleAt);
        given(simulationRun.getLastRebalanceAt()).willReturn(lastCandleAt);
        given(simulationRun.getQuoteCurrency()).willReturn("KRW");
        given(simulationRun.getRebalanceInterval()).willReturn(RebalancePeriod.DAILY);
        given(simulationRun.getTimeframe()).willReturn(Timeframe.ONE_HOUR);
        given(simulationRun.getStrategy()).willReturn(strategy);
        given(simulationRun.beginCandle(org.mockito.ArgumentMatchers.any())).willReturn(false);
        given(strategy.getStrategyDsl()).willReturn(strategyDsl);
        given(asset.getAssetId()).willReturn(assetId);
        given(collectedAssetReadService.findAvailableAt(any(), any(), any())).willReturn(List.of(asset));
        given(simulationPositionRepository.findAllBySimulationRunSimulationIdOrderByIdAssetIdAsc(simulationId))
                .willReturn(List.of());
        given(rebalanceSchedule.shouldRebalance(any(), any(), any(), anyBoolean())).willAnswer(invocation ->
                invocation.getArgument(2, Instant.class).equals(lastCandleAt.plusSeconds(3600))
        );
        given(warmupCandleCountResolver.resolve(strategyDsl)).willReturn(1);
        given(strategyExecutionPipeline.execute(any())).willReturn(new TargetPortfolio(
                List.of(new TargetAssetWeight(assetId, BigDecimal.ONE)), BigDecimal.ZERO
        ));
        given(historicalCandleProvider.load(any(), eq(Timeframe.ONE_HOUR), any(), any())).willAnswer(invocation -> {
            Instant fromInclusive = invocation.getArgument(2, Instant.class);
            if (fromInclusive.equals(lastCandleAt.plusSeconds(3600))) {
                return Map.of(assetId, List.of(candle(assetId, lastCandleAt.plusSeconds(3600), "100", "110")));
            }
            if (fromInclusive.equals(recoveryExecutionAt)) {
                return Map.of(assetId, List.of(candle(assetId, recoveryExecutionAt, "120", "130")));
            }
            return Map.of();
        });

        executionService.process(simulationId, currentExecutionAt.minusSeconds(3600), currentExecutionAt);

        then(historicalCandleProvider).should().load(
                any(), eq(Timeframe.ONE_HOUR), eq(recoveryExecutionAt), eq(currentExecutionAt)
        );
        then(marketPriceCache).shouldHaveNoInteractions();
    }

    private SimulationCandleExecutionService executionService() {
        return new SimulationCandleExecutionService(
                simulationRunRepository,
                simulationPositionRepository,
                simulationTradeRepository,
                simulationEquityRepository,
                simulationResultRepository,
                simulationUniverseAssetRepository,
                collectedAssetReadService,
                historicalCandleProvider,
                warmupCandleCountResolver,
                strategyExecutionPipeline,
                rebalanceOrderPlanner,
                rebalanceSchedule,
                portfolioOrderExecutionService,
                portfolioValuationCalculator,
                executionPolicyProperties,
                marketPriceCache,
                transactionManager,
                failureService
        );
    }

    private LiveMinuteCandle candle(int intervalMinutes) {
        return new LiveMinuteCandle(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "KRW-BTC", intervalMinutes, 1_800_000_000_000L,
                "100", "100", "100", "100", "1", "100", 1_800_000_000_000L, 1L
        );
    }

    private MinuteCandle candle(UUID assetId, Instant openTime, String openPrice, String closePrice) {
        MinuteCandle candle = mock(MinuteCandle.class);
        given(candle.getId()).willReturn(new MinuteCandleId(assetId, 60, openTime));
        given(candle.getOpenPrice()).willReturn(new BigDecimal(openPrice));
        given(candle.getClosePrice()).willReturn(new BigDecimal(closePrice));
        return candle;
    }

    private StrategyDsl strategyDsl() {
        return new StrategyDsl(
                Timeframe.ONE_HOUR,
                new ConditionFilterNode(
                        new FactorOperand(FactorType.RSI, 14),
                        ComparisonOperator.LT,
                        new ValueOperand(BigDecimal.valueOf(30))
                ),
                new Ranking(FactorType.RETURN, 20, RankingDirection.DESC),
                new Selection(5),
                new Rebalance(RebalancePeriod.DAILY)
        );
    }
}
