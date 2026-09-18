package com.ssafy.a107.trading.simulation.service;

import com.ssafy.a107.asset.domain.Asset;
import com.ssafy.a107.candle.domain.MinuteCandle;
import com.ssafy.a107.strategy.domain.dsl.StrategyDsl;
import com.ssafy.a107.strategy.domain.dsl.Timeframe;
import com.ssafy.a107.trading.backtest.engine.marketdata.BacktestWarmupCandleCountResolver;
import com.ssafy.a107.trading.backtest.engine.marketdata.HistoricalCandleProvider;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 전략 실행과 디버그 진단이 같은 봉 준비 판정을 쓰도록 모은다.
 */
public class SimulationStrategyReadinessEvaluator {

    private final HistoricalCandleProvider historicalCandleProvider;
    private final BacktestWarmupCandleCountResolver warmupCandleCountResolver;

    public SimulationStrategyReadinessEvaluator(
            HistoricalCandleProvider historicalCandleProvider,
            BacktestWarmupCandleCountResolver warmupCandleCountResolver
    ) {
        this.historicalCandleProvider = historicalCandleProvider;
        this.warmupCandleCountResolver = warmupCandleCountResolver;
    }

    public Evaluation evaluate(
            StrategyDsl strategyDsl,
            Timeframe timeframe,
            List<Asset> universe,
            Set<UUID> heldAssetIds,
            Instant candleAt,
            boolean hideLatestCompletedCandle
    ) {
        int warmupCandleCount = warmupCandleCountResolver.resolve(strategyDsl);
        Duration candleDuration = Duration.ofMinutes(timeframe.getIntervalMinutes());
        Instant fromInclusive = candleAt.minus(candleDuration.multipliedBy(warmupCandleCount));
        Instant latestCompletedOpenAt = candleAt.minus(candleDuration);

        List<Asset> maturityEligibleAssets = universe.stream()
                .filter(asset -> asset.getTradingSupportStartedAt() == null
                        || !asset.getTradingSupportStartedAt().isAfter(fromInclusive))
                .toList();
        Map<UUID, List<MinuteCandle>> loadedCandles = historicalCandleProvider.load(
                maturityEligibleAssets.stream().map(Asset::getAssetId).toList(),
                timeframe,
                fromInclusive,
                candleAt
        );

        boolean waiting = false;
        Map<UUID, List<MinuteCandle>> readyCandlesByAssetId = new LinkedHashMap<>();
        Map<UUID, AssetReadiness> readinessByAssetId = new LinkedHashMap<>();

        for (Asset asset : universe) {
            UUID assetId = asset.getAssetId();
            boolean held = heldAssetIds.contains(assetId);
            if (!maturityEligibleAssets.contains(asset)) {
                readinessByAssetId.put(assetId, new AssetReadiness(assetId, asset.getExchangeMarketCode(), held,
                        State.EXCLUDED_NEW_LISTING, 0, 0));
                continue;
            }

            List<MinuteCandle> completedCandles = loadedCandles.getOrDefault(assetId, List.of()).stream()
                    .filter(candle -> {
                        Instant openTime = candle.getId().getOpenTime();
                        return !openTime.isBefore(fromInclusive)
                                && !openTime.plus(candleDuration).isAfter(candleAt)
                                && (!hideLatestCompletedCandle || !openTime.equals(latestCompletedOpenAt));
                    })
                    .toList();
            int priorCandleCount = Math.toIntExact(completedCandles.stream()
                    .filter(candle -> candle.getId().getOpenTime().isBefore(latestCompletedOpenAt))
                    .count());

            if (priorCandleCount < warmupCandleCount - 1) {
                State state = held ? State.WAITING_FOR_HELD_ASSET_HISTORY : State.EXCLUDED_INSUFFICIENT_HISTORY;
                readinessByAssetId.put(assetId, new AssetReadiness(assetId, asset.getExchangeMarketCode(), held,
                        state, completedCandles.size(), priorCandleCount));
                waiting |= held;
                continue;
            }
            if (completedCandles.size() < warmupCandleCount) {
                readinessByAssetId.put(assetId, new AssetReadiness(assetId, asset.getExchangeMarketCode(), held,
                        State.WAITING_FOR_LATEST_CANDLE, completedCandles.size(), priorCandleCount));
                waiting = true;
                continue;
            }
            readinessByAssetId.put(assetId, new AssetReadiness(assetId, asset.getExchangeMarketCode(), held,
                    State.READY, completedCandles.size(), priorCandleCount));
            readyCandlesByAssetId.put(assetId, completedCandles);
        }

        if (readyCandlesByAssetId.isEmpty()) waiting = true;
        return new Evaluation(
                waiting ? State.WAITING_FOR_CANDLES : State.READY,
                fromInclusive,
                warmupCandleCount,
                Map.copyOf(readyCandlesByAssetId),
                List.copyOf(readinessByAssetId.values())
        );
    }

    public enum State {
        READY,
        WAITING_FOR_CANDLES,
        WAITING_FOR_LATEST_CANDLE,
        WAITING_FOR_HELD_ASSET_HISTORY,
        EXCLUDED_NEW_LISTING,
        EXCLUDED_INSUFFICIENT_HISTORY
    }

    public record Evaluation(
            State state,
            Instant fromInclusive,
            int warmupCandleCount,
            Map<UUID, List<MinuteCandle>> readyCandlesByAssetId,
            List<AssetReadiness> assets
    ) {
        public boolean isReady() {
            return state == State.READY;
        }
    }

    public record AssetReadiness(
            UUID assetId,
            String marketCode,
            boolean held,
            State state,
            int completedCandleCount,
            int priorCandleCount
    ) {
    }
}
