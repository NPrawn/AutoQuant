package com.ssafy.a107.trading.backtest.engine.marketdata;

import com.ssafy.a107.candle.domain.MinuteCandle;
import com.ssafy.a107.strategy.domain.dsl.Timeframe;

import java.time.Instant;
import java.util.*;

public record BacktestCandleWindow(
        Timeframe timeframe,
        Instant evaluationStart,
        Instant evaluationEnd,
        Map<UUID, List<MinuteCandle>> candlesByAssetId
) {

    public BacktestCandleWindow {
        timeframe = Objects.requireNonNull(timeframe);
        evaluationStart = Objects.requireNonNull(evaluationStart);
        evaluationEnd = Objects.requireNonNull(evaluationEnd);
        candlesByAssetId = copyAndValidate(
                timeframe,
                evaluationEnd,
                candlesByAssetId
        );

        if (!evaluationStart.isBefore(evaluationEnd)) {
            throw new IllegalArgumentException(
                    "Evaluation start must be before end"
            );
        }
    }

    public Map<UUID, List<MinuteCandle>> warmupCandlesByAssetId() {
        return filterByOpenTime(candleOpenTime -> candleOpenTime.isBefore(evaluationStart));
    }

    public Map<UUID, List<MinuteCandle>> evaluationCandlesByAssetId() {
        return filterByOpenTime(candleOpenTime ->
                !candleOpenTime.isBefore(evaluationStart)
                        && candleOpenTime.isBefore(evaluationEnd));
    }

    private Map<UUID, List<MinuteCandle>> filterByOpenTime(
            java.util.function.Predicate<Instant> predicate
    ) {
        Map<UUID, List<MinuteCandle>> filteredCandlesByAssetId = new LinkedHashMap<>();

        for (Map.Entry<UUID, List<MinuteCandle>> entry : candlesByAssetId.entrySet()) {
            List<MinuteCandle> candles = entry.getValue().stream()
                    .filter(candle -> predicate.test(candle.getId().getOpenTime()))
                    .toList();
            filteredCandlesByAssetId.put(entry.getKey(), candles);
        }

        return Collections.unmodifiableMap(filteredCandlesByAssetId);
    }

    private static Map<UUID, List<MinuteCandle>> copyAndValidate(
            Timeframe timeframe,
            Instant evaluationEnd,
            Map<UUID, List<MinuteCandle>> candlesByAssetId
    ) {
        Objects.requireNonNull(candlesByAssetId);

        Map<UUID, List<MinuteCandle>> copiedCandlesByAssetId = new LinkedHashMap<>();

        for (Map.Entry<UUID, List<MinuteCandle>> entry : candlesByAssetId.entrySet()) {
            UUID assetId = Objects.requireNonNull(entry.getKey());
            List<MinuteCandle> candles = List.copyOf(entry.getValue());

            validateCandles(assetId, timeframe, evaluationEnd, candles);
            copiedCandlesByAssetId.put(assetId, candles);
        }

        return Collections.unmodifiableMap(copiedCandlesByAssetId);
    }

    private static void validateCandles(
            UUID assetId,
            Timeframe timeframe,
            Instant evaluationEnd,
            List<MinuteCandle> candles
    ) {
        Instant previousOpenTime = null;

        for (MinuteCandle candle : candles) {
            if (!assetId.equals(candle.getId().getAssetId())) {
                throw new IllegalArgumentException(
                        "Candle asset must match map ket"
                );
            }

            if (candle.getId().getIntervalMinutes() != timeframe.getIntervalMinutes()) {
                throw new IllegalArgumentException(
                        "Candle interval must match timeframe"
                );
            }

            Instant openTime = candle.getId().getOpenTime();
            if (!openTime.isBefore(evaluationEnd)) {
                throw new IllegalArgumentException(
                        "Candle open time must be before evaluation end"
                );
            }

            if (previousOpenTime != null && !previousOpenTime.isBefore(openTime)) {
                throw new IllegalArgumentException(
                        "Candles must be ordered by ascending open time"
                );
            }

            previousOpenTime = openTime;
        }
    }
}
