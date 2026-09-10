package com.ssafy.a107.trading.simulation.service;

import com.ssafy.a107.asset.domain.Asset;
import com.ssafy.a107.asset.domain.Exchange;
import com.ssafy.a107.asset.repository.AssetRepository;
import com.ssafy.a107.candle.domain.MinuteCandle;
import com.ssafy.a107.realtime.domain.LiveMinuteCandle;
import com.ssafy.a107.strategy.engine.StrategyExecutionContext;
import com.ssafy.a107.strategy.engine.StrategyExecutionPipeline;
import com.ssafy.a107.strategy.engine.portfolio.TargetPortfolio;
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
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class SimulationCandleExecutionService {

    private static final Logger log = LoggerFactory.getLogger(SimulationCandleExecutionService.class);
    private static final int MONEY_SCALE = 8;
    private static final int RATE_SCALE = 8;
    private static final int MAX_TRANSIENT_RETRY_ATTEMPTS = 3;
    private static final Duration TRANSIENT_RETRY_DELAY = Duration.ofMillis(100);

    private final SimulationRunRepository simulationRunRepository;
    private final SimulationPositionRepository simulationPositionRepository;
    private final SimulationTradeRepository simulationTradeRepository;
    private final SimulationEquityRepository simulationEquityRepository;
    private final SimulationResultRepository simulationResultRepository;
    private final SimulationUniverseAssetRepository simulationUniverseAssetRepository;
    private final AssetRepository assetRepository;
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
            AssetRepository assetRepository,
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
        this.assetRepository = assetRepository;
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

    public void handle(LiveMinuteCandle candle) {
        marketPriceCache.update(candle);
        com.ssafy.a107.strategy.domain.dsl.Timeframe timeframe = timeframeForInterval(candle.intervalMinutes());
        if (timeframe == null) return;
        Instant candleAt = Instant.ofEpochMilli(candle.openTimeUnixMilli());
        findRunningSimulations(timeframe, candleAt)
                .forEach(simulationRun -> processInTransaction(simulationRun.getSimulationId(), candleAt));
    }

    private List<SimulationRun> findRunningSimulations(
            com.ssafy.a107.strategy.domain.dsl.Timeframe timeframe,
            Instant candleAt
    ) {
        for (int attempt = 1; attempt <= MAX_TRANSIENT_RETRY_ATTEMPTS; attempt++) {
            try {
                return simulationRunRepository.findAllByStatusAndTimeframe(SimulationRunStatus.RUNNING, timeframe);
            } catch (RuntimeException exception) {
                if (!isTransient(exception)) throw exception;
                if (attempt == MAX_TRANSIENT_RETRY_ATTEMPTS) {
                    log.warn("Simulation execution skipped after transient run lookup retries: timeframe={}, candleAt={}, error={}",
                            timeframe, candleAt, exception.getClass().getSimpleName());
                    return List.of();
                }
                log.warn("Retrying simulation run lookup: timeframe={}, candleAt={}, attempt={}, error={}",
                        timeframe, candleAt, attempt, exception.getClass().getSimpleName());
                waitBeforeRetry();
            }
        }
        return List.of();
    }

    private void processInTransaction(UUID simulationId, Instant candleAt) {
        for (int attempt = 1; attempt <= MAX_TRANSIENT_RETRY_ATTEMPTS; attempt++) {
            try {
                transactionTemplate.executeWithoutResult(
                        transactionStatus -> process(simulationId, candleAt)
                );
                return;
            } catch (RuntimeException exception) {
                if (!isTransient(exception)) {
                    log.error("Simulation execution failed: simulationId={}, candleAt={}, error={}",
                            simulationId, candleAt, exception.getClass().getSimpleName());
                    simulationExecutionFailureService.fail(simulationId, exception);
                    return;
                }
                if (attempt == MAX_TRANSIENT_RETRY_ATTEMPTS) {
                    log.warn("Simulation execution skipped after transient retries: simulationId={}, candleAt={}, error={}",
                            simulationId, candleAt, exception.getClass().getSimpleName());
                    return;
                }
                log.warn("Retrying simulation execution: simulationId={}, candleAt={}, attempt={}, error={}",
                        simulationId, candleAt, attempt, exception.getClass().getSimpleName());
                waitBeforeRetry();
            }
        }
    }

    public void process(UUID simulationId, Instant candleAt) {
        SimulationRun simulationRun = simulationRunRepository.findBySimulationIdForUpdate(simulationId).orElse(null);
        if (simulationRun == null || simulationRun.getStatus() != SimulationRunStatus.RUNNING) return;
        List<Asset> universe = assetRepository.findAllAvailableAt(Exchange.UPBIT, simulationRun.getQuoteCurrency(), candleAt);
            List<SimulationPosition> persistedPositions = simulationPositionRepository
                    .findAllBySimulationRunSimulationIdOrderByIdAssetIdAsc(simulationId);
            if (!rebalanceSchedule.shouldRebalance(
                    simulationRun.getRebalanceInterval(),
                    simulationRun.getTimeframe(),
                    candleAt,
                    simulationRun.getLastRebalanceAt() == null
            )) {
                simulationRun.beginCandle(candleAt);
                return;
            }

            TargetPortfolio targetPortfolio = executeStrategy(simulationRun, universe, candleAt);
            Map<UUID, Asset> assetsById = assetsById(universe, persistedPositions);
            Set<UUID> pricedAssetIds = new java.util.HashSet<>(targetPortfolio.assetWeights().stream()
                    .map(targetAssetWeight -> targetAssetWeight.assetId())
                    .toList());
            persistedPositions.forEach(position -> pricedAssetIds.add(position.getAsset().getAssetId()));
            Map<UUID, BigDecimal> currentPrices = marketPriceCache
                    .snapshotAt(pricedAssetIds, simulationRun.getTimeframe().getIntervalMinutes(), candleAt)
                    .orElse(null);
            if (currentPrices == null || !simulationRun.beginCandle(candleAt)) return;
            BacktestPortfolio portfolio = restorePortfolio(simulationRun, persistedPositions);
            List<VirtualOrder> orders = rebalanceOrderPlanner.plan(portfolio, targetPortfolio, currentPrices).stream()
                    .map(order -> virtualOrder(order, candleAt, currentPrices))
                    .toList();
            BacktestOrderExecutionResult executionResult = portfolioOrderExecutionService.execute(
                    portfolio, orders, executionPolicy(simulationRun)
            );

        persistLedger(simulationRun, candleAt, universe, targetPortfolio, assetsById, portfolio, executionResult, currentPrices);
    }

    private TargetPortfolio executeStrategy(SimulationRun simulationRun, List<Asset> universe, Instant candleAt) {
        List<UUID> assetIds = universe.stream().map(Asset::getAssetId).toList();
        int warmupCount = warmupCandleCountResolver.resolve(simulationRun.getStrategy().getStrategyDsl());
        Instant fromInclusive = candleAt.minus(Duration.ofMinutes((long) warmupCount * simulationRun.getTimeframe().getIntervalMinutes()));
        Map<UUID, List<MinuteCandle>> candlesByAssetId = historicalCandleProvider
                .load(assetIds, simulationRun.getTimeframe(), fromInclusive, candleAt)
                .entrySet().stream()
                .filter(entry -> entry.getValue().size() >= warmupCount)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        return strategyExecutionPipeline.execute(new StrategyExecutionContext(
                simulationRun.getStrategy().getStrategyDsl(),
                candleAt,
                candlesByAssetId
        ));
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
                executionPolicyProperties.minimumOrderAmount(), executionPolicyProperties.priceTickSize(),
                executionPolicyProperties.quantityStepSize());
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

    private com.ssafy.a107.strategy.domain.dsl.Timeframe timeframeForInterval(int intervalMinutes) {
        return java.util.Arrays.stream(com.ssafy.a107.strategy.domain.dsl.Timeframe.values())
                .filter(timeframe -> timeframe.getIntervalMinutes() == intervalMinutes)
                .findFirst()
                .orElse(null);
    }
}
