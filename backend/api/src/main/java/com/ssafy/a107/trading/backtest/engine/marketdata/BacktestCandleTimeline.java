package com.ssafy.a107.trading.backtest.engine.marketdata;

import com.ssafy.a107.candle.domain.MinuteCandle;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

public record BacktestCandleTimeline(
        List<Instant> evaluationCandleOpenTimes
) {

    public BacktestCandleTimeline {
        Objects.requireNonNull(evaluationCandleOpenTimes);

        TreeSet<Instant> sortedOpenTimes = new TreeSet<>();
        for (Instant evaluationCandleOpenTime : evaluationCandleOpenTimes) {
            sortedOpenTimes.add(Objects.requireNonNull(evaluationCandleOpenTime));
        }

        evaluationCandleOpenTimes = List.copyOf(sortedOpenTimes);
    }

    public static BacktestCandleTimeline from(
            BacktestCandleWindow candleWindow
    ) {
        Objects.requireNonNull(candleWindow);

        List<Instant> evaluationCandleOpenTimes = candleWindow
                .evaluationCandlesByAssetId()
                .values()
                .stream()
                .flatMap(List::stream)
                .map(MinuteCandle::getId)
                .map(candleId -> candleId.getOpenTime())
                .toList();

        return new BacktestCandleTimeline(evaluationCandleOpenTimes);
    }
}
