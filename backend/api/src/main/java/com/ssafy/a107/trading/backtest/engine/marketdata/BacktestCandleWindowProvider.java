package com.ssafy.a107.trading.backtest.engine.marketdata;

import com.ssafy.a107.candle.domain.MinuteCandle;
import com.ssafy.a107.trading.backtest.domain.BacktestRun;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Component
public class BacktestCandleWindowProvider {

    private final HistoricalCandleProvider historicalCandleProvider;
    private final BacktestWarmupCandleCountResolver warmupCandleCountResolver;

    public BacktestCandleWindowProvider(
            HistoricalCandleProvider historicalCandleProvider,
            BacktestWarmupCandleCountResolver warmupCandleCountResolver
    ) {
        this.historicalCandleProvider = historicalCandleProvider;
        this.warmupCandleCountResolver = warmupCandleCountResolver;
    }

    public BacktestCandleWindow load(
            BacktestRun backtestRun,
            Collection<UUID> assetIds
    ) {
        Objects.requireNonNull(backtestRun);
        Objects.requireNonNull(assetIds);

        int warmupCandleCount = warmupCandleCountResolver.resolve(
                backtestRun.getStrategy().getStrategyDsl()
        );
        Duration candleDuration = Duration.ofMinutes(
                backtestRun.getTimeframe().getIntervalMinutes()
        );
        Instant loadStart = backtestRun.getPeriodStart().minus(
                candleDuration.multipliedBy(warmupCandleCount)
        );

        Map<UUID, List<MinuteCandle>> candlesByAssetId =
                historicalCandleProvider.load(
                        assetIds,
                        backtestRun.getTimeframe(),
                        loadStart,
                        backtestRun.getPeriodEnd().plus(candleDuration)
                );

        return new BacktestCandleWindow(
                backtestRun.getTimeframe(),
                backtestRun.getPeriodStart(),
                backtestRun.getPeriodEnd(),
                candlesByAssetId
        );
    }
}
