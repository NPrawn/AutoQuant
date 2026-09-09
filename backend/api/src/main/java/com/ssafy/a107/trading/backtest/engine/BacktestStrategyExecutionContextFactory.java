package com.ssafy.a107.trading.backtest.engine;

import com.ssafy.a107.candle.domain.MinuteCandle;
import com.ssafy.a107.strategy.engine.StrategyExecutionContext;
import com.ssafy.a107.trading.backtest.domain.BacktestRun;
import com.ssafy.a107.trading.backtest.engine.marketdata.BacktestCandleWindow;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.UUID;

@Component
public class BacktestStrategyExecutionContextFactory {

    public StrategyExecutionContext create(
            BacktestRun backtestRun,
            BacktestCandleWindow candleWindow,
            Instant candleOpenTime
    ) {
        return create(
                backtestRun,
                candleWindow,
                candleOpenTime,
                candleWindow.candlesByAssetId().keySet()
        );
    }

    public StrategyExecutionContext create(
            BacktestRun backtestRun,
            BacktestCandleWindow candleWindow,
            Instant candleOpenTime,
            Collection<UUID> assetIds
    ) {
        Objects.requireNonNull(backtestRun);
        Objects.requireNonNull(candleWindow);
        Objects.requireNonNull(candleOpenTime);
        Objects.requireNonNull(assetIds);

        if (backtestRun.getTimeframe() != candleWindow.timeframe()) {
            throw new IllegalArgumentException(
                    "Backtest timeframe must match candle window"
            );
        }

        if (candleOpenTime.isBefore(candleWindow.evaluationStart())
                || !candleOpenTime.isBefore(candleWindow.evaluationEnd())) {
            throw new IllegalArgumentException(
                    "Candle open time must be within evaluation window"
            );
        }

        Duration candleDuration = Duration.ofMinutes(
                backtestRun.getTimeframe().getIntervalMinutes()
        );
        Instant evaluationAt = candleOpenTime.plus(candleDuration);

        if (evaluationAt.isAfter(candleWindow.evaluationEnd())) {
            throw new IllegalArgumentException(
                    "Candle must close within evaluation window"
            );
        }

        Map<UUID, List<MinuteCandle>> candlesByAssetId = new LinkedHashMap<>();

        for (UUID assetId : new TreeSet<>(assetIds)) {
            List<MinuteCandle> candles = candleWindow.candlesByAssetId()
                    .get(assetId);
            if (candles == null) {
                throw new IllegalArgumentException(
                        "Candle window does not contain requested asset"
                );
            }

            List<MinuteCandle> availableCandles = candles.stream()
                    .filter(candle -> !candle.getId().getOpenTime()
                            .plus(candleDuration)
                            .isAfter(evaluationAt))
                    .toList();
            candlesByAssetId.put(assetId, availableCandles);
        }

        return new StrategyExecutionContext(
                backtestRun.getStrategy().getStrategyDsl(),
                evaluationAt,
                candlesByAssetId
        );
    }
}
