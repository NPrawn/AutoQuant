package com.ssafy.a107.trading.simulation.service;

import com.ssafy.a107.asset.domain.Asset;
import com.ssafy.a107.asset.domain.Exchange;
import com.ssafy.a107.asset.service.CollectedAssetReadService;
import com.ssafy.a107.candle.domain.MinuteCandle;
import com.ssafy.a107.realtime.domain.LiveMinuteCandle;
import com.ssafy.a107.strategy.engine.StrategyExecutionContext;
import com.ssafy.a107.strategy.engine.StrategyExecutionPipeline;
import com.ssafy.a107.strategy.engine.portfolio.TargetPortfolio;
import com.ssafy.a107.strategy.domain.dsl.Timeframe;
import com.ssafy.a107.trading.backtest.domain.PositionSide;
import com.ssafy.a107.trading.backtest.engine.BacktestRebalanceSchedule;
import com.ssafy.a107.trading.backtest.engine.execution.BacktestExecutionPolicyProperties;
import com.ssafy.a107.trading.backtest.engine.execution.VirtualExecutionPolicy;
import com.ssafy.a107.trading.backtest.engine.execution.VirtualOrder;
import com.ssafy.a107.trading.backtest.engine.portfolio.BacktestPortfolio;
import com.ssafy.a107.trading.backtest.engine.portfolio.BacktestPortfolioOrderExecutionService;
import com.ssafy.a107.trading.backtest.engine.portfolio.BacktestPosition;
import com.ssafy.a107.trading.backtest.engine.portfolio.BacktestRebalanceOrder;
import com.ssafy.a107.trading.backtest.engine.portfolio.BacktestRebalanceOrderPlanner;
import com.ssafy.a107.trading.backtest.engine.portfolio.BacktestOrderExecutionResult;
import com.ssafy.a107.trading.backtest.engine.portfolio.PortfolioValuationCalculator;
import com.ssafy.a107.trading.backtest.engine.marketdata.HistoricalCandleProvider;
import com.ssafy.a107.trading.backtest.engine.marketdata.BacktestWarmupCandleCountResolver;
import com.ssafy.a107.trading.backtest.engine.execution.VirtualFill;
import com.ssafy.a107.trading.simulation.domain.SimulationEquity;
import com.ssafy.a107.trading.simulation.domain.SimulationEquityId;
import com.ssafy.a107.trading.simulation.domain.SimulationPosition;
import com.ssafy.a107.trading.simulation.domain.SimulationResult;
import com.ssafy.a107.trading.simulation.domain.SimulationRun;
import com.ssafy.a107.trading.simulation.domain.SimulationRunStatus;
import com.ssafy.a107.trading.simulation.domain.SimulationTrade;
import com.ssafy.a107.trading.simulation.domain.SimulationUniverseAsset;
import com.ssafy.a107.trading.simulation.domain.SimulationUniverseAssetId;
import com.ssafy.a107.trading.simulation.repository.SimulationEquityRepository;
import com.ssafy.a107.trading.simulation.repository.SimulationPositionRepository;
import com.ssafy.a107.trading.simulation.repository.SimulationResultRepository;
import com.ssafy.a107.trading.simulation.repository.SimulationRunRepository;
import com.ssafy.a107.trading.simulation.repository.SimulationTradeRepository;
import com.ssafy.a107.trading.simulation.repository.SimulationUniverseAssetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

@Service
public class SimulationCandleExecutionService {

    private static final Logger log = LoggerFactory.getLogger(SimulationCandleExecutionService.class);
    private static final int MONEY_SCALE = 8;
    private static final int RATE_SCALE = 8;
    private static final int MAX_TRANSIENT_RETRY_ATTEMPTS = 3;
    private static final Duration TRANSIENT_RETRY_DELAY = Duration.ofMillis(100);
    private static final int RUN_LOOKUP_PAGE_SIZE = 100;

    private final SimulationRunRepository simulationRunRepository;
    private final SimulationPositionRepository simulationPositionRepository;
    private final SimulationTradeRepository simulationTradeRepository;
    private final SimulationEquityRepository simulationEquityRepository;
    private final SimulationResultRepository simulationResultRepository;
    private final SimulationUniverseAssetRepository simulationUniverseAssetRepository;
    private final CollectedAssetReadService collectedAssetReadService;
    private final HistoricalCandleProvider historicalCandleProvider;
    private final BacktestWarmupCandleCountResolver warmupCandleCountResolver;
    private final StrategyExecutionPipeline strategyExecutionPipeline;
    private final BacktestRebalanceOrderPlanner rebalanceOrderPlanner;
    private final BacktestRebalanceSchedule rebalanceSchedule;
    private final BacktestPortfolioOrderExecutionService portfolioOrderExecutionService;
    private final PortfolioValuationCalculator portfolioValuationCalculator;
    private final BacktestExecutionPolicyProperties executionPolicyProperties;
    private final SimulationMarketPriceCache marketPriceCache;
    private final TransactionTemplate transactionTemplate;
    private final SimulationExecutionFailureService simulationExecutionFailureService;

    public SimulationCandleExecutionService(
            SimulationRunRepository simulationRunRepository,
            SimulationPositionRepository simulationPositionRepository,
            SimulationTradeRepository simulationTradeRepository,
            SimulationEquityRepository simulationEquityRepository,
            SimulationResultRepository simulationResultRepository,
            SimulationUniverseAssetRepository simulationUniverseAssetRepository,
            CollectedAssetReadService collectedAssetReadService,
            HistoricalCandleProvider historicalCandleProvider,
            BacktestWarmupCandleCountResolver warmupCandleCountResolver,
            StrategyExecutionPipeline strategyExecutionPipeline,
            BacktestRebalanceOrderPlanner rebalanceOrderPlanner,
            BacktestRebalanceSchedule rebalanceSchedule,
            BacktestPortfolioOrderExecutionService portfolioOrderExecutionService,
            PortfolioValuationCalculator portfolioValuationCalculator,
            BacktestExecutionPolicyProperties executionPolicyProperties,
            SimulationMarketPriceCache marketPriceCache,
            PlatformTransactionManager transactionManager,
            SimulationExecutionFailureService simulationExecutionFailureService
    ) {
        this.simulationRunRepository = simulationRunRepository;
        this.simulationPositionRepository = simulationPositionRepository;
        this.simulationTradeRepository = simulationTradeRepository;
        this.simulationEquityRepository = simulationEquityRepository;
        this.simulationResultRepository = simulationResultRepository;
        this.simulationUniverseAssetRepository = simulationUniverseAssetRepository;
        this.collectedAssetReadService = collectedAssetReadService;
        this.historicalCandleProvider = historicalCandleProvider;
        this.warmupCandleCountResolver = warmupCandleCountResolver;
        this.strategyExecutionPipeline = strategyExecutionPipeline;
        this.rebalanceOrderPlanner = rebalanceOrderPlanner;
        this.rebalanceSchedule = rebalanceSchedule;
        this.portfolioOrderExecutionService = portfolioOrderExecutionService;
        this.portfolioValuationCalculator = portfolioValuationCalculator;
        this.executionPolicyProperties = executionPolicyProperties;
        this.marketPriceCache = marketPriceCache;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.simulationExecutionFailureService = simulationExecutionFailureService;
    }

    public boolean handle(LiveMinuteCandle candle) {
        marketPriceCache.update(candle);
        Timeframe timeframe = timeframeForInterval(candle.intervalMinutes());
        if (timeframe == null) return false;
        Instant executionAt = Instant.ofEpochMilli(candle.openTimeUnixMilli());
        Instant evaluationCandleOpenAt = executionAt.minus(candleDuration(timeframe));
        boolean retryRequired = false;
        for (int page = 0; ; page++) {
            RunLookup runLookup = findRunningSimulationIds(timeframe, executionAt, page);
            if (runLookup.retryRequired()) return true;
            for (UUID simulationId : runLookup.simulationIds()) {
                retryRequired |= processInTransaction(simulationId, evaluationCandleOpenAt, executionAt);
            }
            if (runLookup.simulationIds().size() < RUN_LOOKUP_PAGE_SIZE) return retryRequired;
        }
    }

    private RunLookup findRunningSimulationIds(
            Timeframe timeframe,
            Instant executionAt,
            int page
    ) {
        for (int attempt = 1; attempt <= MAX_TRANSIENT_RETRY_ATTEMPTS; attempt++) {
            try {
                return new RunLookup(
                        simulationRunRepository.findSimulationIdsByStatusAndTimeframe(
                                SimulationRunStatus.RUNNING,
                                timeframe,
                                PageRequest.of(page, RUN_LOOKUP_PAGE_SIZE)
                        ),
                        false
                );
            } catch (RuntimeException exception) {
                if (!isTransient(exception)) throw exception;
                if (attempt == MAX_TRANSIENT_RETRY_ATTEMPTS) {
                    log.warn("Simulation execution skipped after transient run lookup retries: timeframe={}, candleAt={}, error={}",
                            timeframe, executionAt, exception.getClass().getSimpleName());
                    return new RunLookup(List.of(), true);
                }
                log.warn("Retrying simulation run lookup: timeframe={}, candleAt={}, attempt={}, error={}",
                        timeframe, executionAt, attempt, exception.getClass().getSimpleName());
                waitBeforeRetry();
            }
        }
        return new RunLookup(List.of(), true);
    }

    private boolean processInTransaction(UUID simulationId, Instant evaluationCandleOpenAt, Instant executionAt) {
        for (int attempt = 1; attempt <= MAX_TRANSIENT_RETRY_ATTEMPTS; attempt++) {
            try {
                return Boolean.TRUE.equals(transactionTemplate.execute(
                        transactionStatus -> process(simulationId, evaluationCandleOpenAt, executionAt)
                ));
            } catch (RuntimeException exception) {
                if (!isTransient(exception)) {
                    log.error("Simulation execution failed: simulationId={}, candleAt={}, error={}",
                            simulationId, executionAt, exception.getClass().getSimpleName());
                    simulationExecutionFailureService.fail(simulationId, exception);
                    return false;
                }
                if (attempt == MAX_TRANSIENT_RETRY_ATTEMPTS) {
                    log.warn("Simulation execution skipped after transient retries: simulationId={}, candleAt={}, error={}",
                            simulationId, executionAt, exception.getClass().getSimpleName());
                    return true;
                }
                log.warn("Retrying simulation execution: simulationId={}, candleAt={}, attempt={}, error={}",
                        simulationId, executionAt, attempt, exception.getClass().getSimpleName());
                waitBeforeRetry();
            }
        }
        return true;
    }

    public boolean process(UUID simulationId, Instant evaluationCandleOpenAt, Instant executionAt) {
        SimulationRun simulationRun = simulationRunRepository.findBySimulationIdForUpdate(simulationId).orElse(null);
        if (simulationRun == null || simulationRun.getStatus() != SimulationRunStatus.RUNNING) return false;
        if (executionAt.isBefore(simulationRun.getStartedAt())) return false;
        if (recoverMissedCandles(simulationRun, executionAt)) return true;
        return processCandle(
                simulationRun,
                evaluationCandleOpenAt,
                executionAt,
                assetIds -> marketPriceCache.snapshotAt(
                        assetIds,
                        simulationRun.getTimeframe().getIntervalMinutes(),
                        executionAt
                )
        ) == CandleProcessResult.NOT_READY;
    }

    private boolean recoverMissedCandles(SimulationRun simulationRun, Instant currentExecutionAt) {
        Instant lastCandleAt = simulationRun.getLastCandleAt();
        if (lastCandleAt == null) return false;

        Duration candleDuration = candleDuration(simulationRun.getTimeframe());
        Instant recoveryExecutionAt = firstRecoveryCandleAt(lastCandleAt, simulationRun.getStartedAt(), candleDuration);
        while (recoveryExecutionAt.isBefore(currentExecutionAt)) {
            Instant recoveryCandleAt = recoveryExecutionAt;
            Instant recoveryEvaluationCandleOpenAt = recoveryCandleAt.minus(candleDuration);
            CandleProcessResult result = processCandle(
                    simulationRun,
                    recoveryEvaluationCandleOpenAt,
                    recoveryCandleAt,
                    assetIds -> loadOpenPrices(
                            assetIds,
                            simulationRun.getTimeframe(),
                            recoveryCandleAt,
                            recoveryCandleAt.plus(candleDuration)
                    )
            );
            if (result == CandleProcessResult.NOT_READY) return true;
            recoveryExecutionAt = recoveryExecutionAt.plus(candleDuration);
        }
        return false;
    }

    private Instant firstRecoveryCandleAt(Instant lastCandleAt, Instant startedAt, Duration candleDuration) {
        Instant recoveryExecutionAt = lastCandleAt.plus(candleDuration);
        if (!recoveryExecutionAt.isBefore(startedAt)) return recoveryExecutionAt;

        long millisecondsToSkip = Duration.between(recoveryExecutionAt, startedAt).toMillis();
        long candleDurationMillis = candleDuration.toMillis();
        long skippedCandleCount = (millisecondsToSkip + candleDurationMillis - 1) / candleDurationMillis;
        return recoveryExecutionAt.plus(candleDuration.multipliedBy(skippedCandleCount));
    }

    private CandleProcessResult processCandle(
            SimulationRun simulationRun,
            Instant evaluationCandleOpenAt,
            Instant executionAt,
            Function<Collection<UUID>, java.util.Optional<Map<UUID, BigDecimal>>> executionPriceLoader
    ) {
        Instant lastCandleAt = simulationRun.getLastCandleAt();
        if (lastCandleAt != null && !executionAt.isAfter(lastCandleAt)) return CandleProcessResult.IGNORED;
        List<Asset> universe = collectedAssetReadService.findAvailableAt(
                Exchange.UPBIT, simulationRun.getQuoteCurrency(), evaluationCandleOpenAt
        );
        List<SimulationPosition> persistedPositions = simulationPositionRepository
                .findAllBySimulationRunSimulationIdOrderByIdAssetIdAsc(simulationRun.getSimulationId());
        if (!rebalanceSchedule.shouldRebalance(
                simulationRun.getRebalanceInterval(),
                simulationRun.getTimeframe(),
                evaluationCandleOpenAt,
                simulationRun.getLastRebalanceAt() == null
        )) {
            return simulationRun.beginCandle(executionAt)
                    ? CandleProcessResult.ADVANCED
                    : CandleProcessResult.IGNORED;
        }

        Set<UUID> heldAssetIds = new java.util.HashSet<>();
        persistedPositions.forEach(position -> heldAssetIds.add(position.getAsset().getAssetId()));
        Optional<TargetPortfolio> strategyResult = executeStrategy(simulationRun, universe, executionAt, heldAssetIds);
        if (strategyResult.isEmpty()) return CandleProcessResult.NOT_READY;
        TargetPortfolio targetPortfolio = strategyResult.get();
        Map<UUID, Asset> assetsById = assetsById(universe, persistedPositions);
        Set<UUID> pricedAssetIds = new java.util.HashSet<>(targetPortfolio.assetWeights().stream()
                .map(targetAssetWeight -> targetAssetWeight.assetId())
                .toList());
        pricedAssetIds.addAll(heldAssetIds);
        Map<UUID, BigDecimal> evaluationPrices = loadClosePrices(
                pricedAssetIds, simulationRun.getTimeframe(), evaluationCandleOpenAt, executionAt
        ).orElse(null);
        Map<UUID, BigDecimal> executionPrices = executionPriceLoader.apply(pricedAssetIds).orElse(null);
        if (evaluationPrices == null || executionPrices == null) return CandleProcessResult.NOT_READY;
        if (!simulationRun.beginCandle(executionAt)) return CandleProcessResult.IGNORED;
        BacktestPortfolio portfolio = restorePortfolio(simulationRun, persistedPositions);
        List<VirtualOrder> orders = rebalanceOrderPlanner.plan(portfolio, targetPortfolio, evaluationPrices).stream()
                .map(order -> virtualOrder(order, executionAt, executionPrices))
                .toList();
        BacktestOrderExecutionResult executionResult = portfolioOrderExecutionService.execute(
                portfolio, orders, executionPolicy(simulationRun)
        );

        persistLedger(simulationRun, executionAt, universe, targetPortfolio, assetsById, portfolio, executionResult, executionPrices);
        return CandleProcessResult.ADVANCED;
    }

    private java.util.Optional<Map<UUID, BigDecimal>> loadClosePrices(
            Collection<UUID> assetIds,
            Timeframe timeframe,
            Instant evaluationCandleOpenAt,
            Instant executionAt
    ) {
        if (assetIds.isEmpty()) return java.util.Optional.of(Map.of());
        Map<UUID, List<MinuteCandle>> candlesByAssetId = historicalCandleProvider.load(
                assetIds, timeframe, evaluationCandleOpenAt, executionAt
        );
        Map<UUID, BigDecimal> closePrices = new HashMap<>();
        for (UUID assetId : assetIds) {
            MinuteCandle candle = candlesByAssetId.getOrDefault(assetId, List.of()).stream()
                    .filter(candidate -> candidate.getId().getOpenTime().equals(evaluationCandleOpenAt))
                    .findFirst()
                    .orElse(null);
            if (candle == null) return java.util.Optional.empty();
            closePrices.put(assetId, candle.getClosePrice());
        }
        return java.util.Optional.of(Map.copyOf(closePrices));
    }

    private java.util.Optional<Map<UUID, BigDecimal>> loadOpenPrices(
            Collection<UUID> assetIds,
            Timeframe timeframe,
            Instant executionCandleOpenAt,
            Instant toExclusive
    ) {
        if (assetIds.isEmpty()) return java.util.Optional.of(Map.of());
        Map<UUID, List<MinuteCandle>> candlesByAssetId = historicalCandleProvider.load(
                assetIds, timeframe, executionCandleOpenAt, toExclusive
        );
        Map<UUID, BigDecimal> openPrices = new HashMap<>();
        for (UUID assetId : assetIds) {
            MinuteCandle candle = candlesByAssetId.getOrDefault(assetId, List.of()).stream()
                    .filter(candidate -> candidate.getId().getOpenTime().equals(executionCandleOpenAt))
                    .findFirst()
                    .orElse(null);
            if (candle == null) return java.util.Optional.empty();
            openPrices.put(assetId, candle.getOpenPrice());
        }
        return java.util.Optional.of(Map.copyOf(openPrices));
    }

    private Optional<TargetPortfolio> executeStrategy(
            SimulationRun simulationRun, List<Asset> universe, Instant candleAt, Set<UUID> heldAssetIds
    ) {
        int warmupCount = warmupCandleCountResolver.resolve(simulationRun.getStrategy().getStrategyDsl());
        Duration candleDuration = candleDuration(simulationRun.getTimeframe());
        Instant fromInclusive = candleAt.minus(candleDuration.multipliedBy(warmupCount));
        Instant latestCompletedOpenAt = candleAt.minus(candleDuration);
        // 상장 전 봉은 기다려도 생기지 않는다. 시작일이 없으면 실제 이력으로 판단한다.
        List<UUID> assetIds = universe.stream()
                .filter(asset -> asset.getTradingSupportStartedAt() == null
                        || !asset.getTradingSupportStartedAt().isAfter(fromInclusive))
                .map(Asset::getAssetId)
                .toList();
        Map<UUID, List<MinuteCandle>> loadedCandles = historicalCandleProvider.load(
                assetIds, simulationRun.getTimeframe(), fromInclusive, candleAt
        );
        Map<UUID, List<MinuteCandle>> candlesByAssetId = new LinkedHashMap<>();
        for (UUID assetId : assetIds) {
            List<MinuteCandle> completedCandles = loadedCandles.getOrDefault(assetId, List.of()).stream()
                    .filter(candle -> {
                        Instant openTime = candle.getId().getOpenTime();
                        return !openTime.isBefore(fromInclusive)
                                && !openTime.plus(candleDuration).isAfter(candleAt);
                    })
                    .toList();
            long priorCandleCount = completedCandles.stream()
                    .filter(candle -> candle.getId().getOpenTime().isBefore(latestCompletedOpenAt))
                    .count();
            // 과거 이력부터 부족한 종목은 계산할 수 없다. 수집 중단 종목이 전체 실행을 막지 않게 한다.
            if (priorCandleCount < warmupCount - 1) {
                if (heldAssetIds.contains(assetId)) return Optional.empty();
                continue;
            }
            // 직전까지 이력이 충분했던 종목은 새 확정 봉을 기다린다. 저장 순서가 순위를 바꾸면 안 된다.
            if (completedCandles.size() < warmupCount) return Optional.empty();
            candlesByAssetId.put(assetId, completedCandles);
        }
        // 계산할 데이터 자체가 없는 상태를 정상적인 현금 보유 결정으로 확정하지 않는다.
        if (candlesByAssetId.isEmpty()) return Optional.empty();
        return Optional.of(strategyExecutionPipeline.execute(new StrategyExecutionContext(
                simulationRun.getStrategy().getStrategyDsl(),
                candleAt,
                candlesByAssetId
        )));
    }

    private BacktestPortfolio restorePortfolio(SimulationRun simulationRun, List<SimulationPosition> persistedPositions) {
        List<BacktestPosition> positions = persistedPositions.stream()
                .map(position -> new BacktestPosition(position.getAsset().getAssetId(), position.getId().getPositionSide(),
                        position.getQuantity(), position.getAverageEntryPrice()))
                .toList();
        return new BacktestPortfolio(simulationRun.getCashBalance(), positions);
    }

    private VirtualOrder virtualOrder(BacktestRebalanceOrder order, Instant candleAt, Map<UUID, BigDecimal> prices) {
        return new VirtualOrder(order.assetId(), PositionSide.LONG, order.side(), candleAt, candleAt,
                prices.get(order.assetId()), order.requestedNotional());
    }

    private VirtualExecutionPolicy executionPolicy(SimulationRun simulationRun) {
        return new VirtualExecutionPolicy(simulationRun.getFeeRate(), simulationRun.getSlippageBps(),
                executionPolicyProperties.minimumOrderAmount(), executionPolicyProperties.quantityStepSize());
    }

    private void persistLedger(SimulationRun simulationRun, Instant candleAt, List<Asset> universe,
                               TargetPortfolio targetPortfolio, Map<UUID, Asset> assetsById, BacktestPortfolio portfolio,
                               BacktestOrderExecutionResult executionResult, Map<UUID, BigDecimal> currentPrices) {
        UUID simulationId = simulationRun.getSimulationId();
        persistUniverse(simulationRun, candleAt, universe, targetPortfolio);
        for (VirtualFill fill : executionResult.fills()) {
            simulationTradeRepository.save(new SimulationTrade(simulationRun, assetsById.get(fill.order().assetId()),
                    fill.order().positionSide(), fill.order().side(), fill.order().signalAt(), fill.order().filledAt(),
                    fill.executedPrice(), fill.quantity(), fill.notional(), fill.feeAmount(), fill.slippageAmount(), "REBALANCE"));
        }
        simulationPositionRepository.deleteAllBySimulationRunSimulationId(simulationId);
        simulationPositionRepository.flush();
        for (BacktestPosition position : portfolio.getPositions()) {
            simulationPositionRepository.save(new SimulationPosition(simulationRun, assetsById.get(position.assetId()),
                    position.positionSide(), position.quantity(), position.averageEntryPrice(), candleAt));
        }
        simulationRun.changeCashBalance(portfolio.getCashBalance());
        simulationRun.markRebalanced(candleAt);
        BigDecimal totalAssetValue = portfolioValuationCalculator.calculateTotalAssetValue(portfolio, currentPrices);
        BigDecimal peakAssetValue = simulationEquityRepository.findMaximumTotalAssetValueBySimulationId(simulationId);
        if (peakAssetValue == null || peakAssetValue.compareTo(totalAssetValue) < 0) peakAssetValue = totalAssetValue;
        BigDecimal returnRate = totalAssetValue.subtract(simulationRun.getInitialCapital())
                .divide(simulationRun.getInitialCapital(), MathContext.DECIMAL128).setScale(RATE_SCALE, RoundingMode.HALF_UP);
        BigDecimal drawdownRate = peakAssetValue.subtract(totalAssetValue)
                .divide(peakAssetValue, MathContext.DECIMAL128).setScale(RATE_SCALE, RoundingMode.HALF_UP);
        simulationEquityRepository.save(new SimulationEquity(simulationRun, new SimulationEquityId(simulationId, candleAt),
                totalAssetValue, portfolio.getCashBalance(), returnRate, drawdownRate));
        long tradeCount = simulationTradeRepository.countBySimulationRunSimulationId(simulationId);
        BigDecimal totalFee = simulationTradeRepository.sumFeeAmountBySimulationId(simulationId);
        simulationResultRepository.findById(simulationId).ifPresentOrElse(
                result -> result.update(candleAt, totalAssetValue, returnRate, drawdownRate, null, null, null,
                        Math.toIntExact(tradeCount), totalFee),
                () -> simulationResultRepository.save(new SimulationResult(simulationRun, candleAt, totalAssetValue,
                        returnRate, drawdownRate, null, null, null, Math.toIntExact(tradeCount), totalFee))
        );
    }

    private void persistUniverse(SimulationRun simulationRun, Instant candleAt, List<Asset> universe, TargetPortfolio targetPortfolio) {
        Map<UUID, Integer> selectedRanks = new HashMap<>();
        for (int index = 0; index < targetPortfolio.assetWeights().size(); index++) {
            selectedRanks.put(targetPortfolio.assetWeights().get(index).assetId(), index + 1);
        }
        List<SimulationUniverseAsset> universeAssets = new ArrayList<>();
        for (Asset asset : universe) {
            Integer rank = selectedRanks.get(asset.getAssetId());
            universeAssets.add(new SimulationUniverseAsset(
                    new SimulationUniverseAssetId(simulationRun.getSimulationId(), candleAt, asset.getAssetId()),
                    simulationRun, asset, rank == null ? null : rank.shortValue(), rank != null
            ));
        }
        simulationUniverseAssetRepository.saveAll(universeAssets);
    }

    private Map<UUID, Asset> assetsById(List<Asset> universe, Collection<SimulationPosition> positions) {
        Map<UUID, Asset> assetsById = new HashMap<>();
        universe.forEach(asset -> assetsById.put(asset.getAssetId(), asset));
        positions.forEach(position -> assetsById.put(position.getAsset().getAssetId(), position.getAsset()));
        return assetsById;
    }

    private boolean isTransient(RuntimeException exception) {
        return exception instanceof TransientDataAccessException
                || exception instanceof CannotCreateTransactionException;
    }

    private void waitBeforeRetry() {
        try {
            Thread.sleep(TRANSIENT_RETRY_DELAY);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Simulation execution retry was interrupted", exception);
        }
    }

    private Duration candleDuration(Timeframe timeframe) {
        return Duration.ofMinutes(timeframe.getIntervalMinutes());
    }

    public static boolean supportsInterval(int intervalMinutes) {
        return timeframeForInterval(intervalMinutes) != null;
    }

    private static Timeframe timeframeForInterval(int intervalMinutes) {
        return java.util.Arrays.stream(Timeframe.values())
                .filter(timeframe -> timeframe.getIntervalMinutes() == intervalMinutes)
                .findFirst()
                .orElse(null);
    }

    private record RunLookup(List<UUID> simulationIds, boolean retryRequired) {
    }

    private enum CandleProcessResult {
        ADVANCED,
        IGNORED,
        NOT_READY
    }
}
