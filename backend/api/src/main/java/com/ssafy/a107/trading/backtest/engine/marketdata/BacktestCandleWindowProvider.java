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
    private final ContestDatasetMarketData contestDatasetMarketData;

    public BacktestCandleWindowProvider(
            HistoricalCandleProvider historicalCandleProvider,
            BacktestWarmupCandleCountResolver warmupCandleCountResolver,
            ContestDatasetMarketData contestDatasetMarketData
    ) {
        this.historicalCandleProvider = historicalCandleProvider;
        this.warmupCandleCountResolver = warmupCandleCountResolver;
        this.contestDatasetMarketData = contestDatasetMarketData;
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

        Instant loadEnd = backtestRun.getPeriodEnd().plus(candleDuration);

        // 대회 백테스트는 데이터셋 캔들만 본다 (ADR-0014).
        Map<UUID, List<MinuteCandle>> candlesByAssetId = backtestRun.isContest()
                ? contestDatasetMarketData.loadCandles(
                        backtestRun.getContestDatasetId(),
                        assetIds,
                        backtestRun.getTimeframe(),
                        loadStart,
                        loadEnd
                )
                : historicalCandleProvider.load(
                        assetIds,
                        backtestRun.getTimeframe(),
                        loadStart,
                        loadEnd
                );

        return new BacktestCandleWindow(
                backtestRun.getTimeframe(),
                backtestRun.getPeriodStart(),
                backtestRun.getPeriodEnd(),
                candlesByAssetId
        );
    }
}
