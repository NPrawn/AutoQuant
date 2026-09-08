package com.ssafy.a107.trading.backtest.engine;

import com.ssafy.a107.candle.domain.MinuteCandle;
import com.ssafy.a107.strategy.engine.StrategyExecutionContext;
import com.ssafy.a107.trading.backtest.domain.BacktestRun;
import com.ssafy.a107.trading.backtest.engine.marketdata.BacktestCandleWindow;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Component
public class BacktestStrategyExecutionContextFactory {

    public StrategyExecutionContext create(
            BacktestRun backtestRun,
            BacktestCandleWindow candleWindow,
            Instant candleOpenTime
    ) {
        Objects.requireNonNull(backtestRun);
        Objects.requireNonNull(candleWindow);
        Objects.requireNonNull(candleOpenTime);

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

        for (Map.Entry<UUID, List<MinuteCandle>> entry
                : candleWindow.candlesByAssetId().entrySet()) {
            List<MinuteCandle> availableCandles = entry.getValue().stream()
                    .filter(candle -> !candle.getId().getOpenTime()
                            .plus(candleDuration)
                            .isAfter(evaluationAt))
                    .toList();
            candlesByAssetId.put(entry.getKey(), availableCandles);
        }

        return new StrategyExecutionContext(
                backtestRun.getStrategy().getStrategyDsl(),
                evaluationAt,
                candlesByAssetId
        );
    }
}
