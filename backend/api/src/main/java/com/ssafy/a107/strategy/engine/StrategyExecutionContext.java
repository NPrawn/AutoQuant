package com.ssafy.a107.strategy.engine;

import com.ssafy.a107.candle.domain.MinuteCandle;
import com.ssafy.a107.strategy.domain.dsl.StrategyDsl;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

public record StrategyExecutionContext(
        StrategyDsl strategyDsl,
        Instant evaluationAt,
        Map<UUID, List<MinuteCandle>> candlesByAssetId
) {

    public StrategyExecutionContext {
        strategyDsl = requireStrategyDslWithTimeframe(strategyDsl);
        evaluationAt = Objects.requireNonNull(evaluationAt);
        candlesByAssetId = copyAndValidate(
                strategyDsl,
                evaluationAt,
                candlesByAssetId
        );
    }

    private static StrategyDsl requireStrategyDslWithTimeframe(StrategyDsl strategyDsl) {
        if (strategyDsl == null) {
            throw new StrategyExecutionException("Unsupported strategy DSL: strategy DSL is required");
        }
        if (strategyDsl.timeframe() == null) {
            throw new StrategyExecutionException("Unsupported strategy DSL: timeframe is required");
        }
        return strategyDsl;
    }

    private static Map<UUID, List<MinuteCandle>> copyAndValidate(
            StrategyDsl strategyDsl,
            Instant evaluationAt,
            Map<UUID, List<MinuteCandle>> candlesByAssetId
    ) {
        Objects.requireNonNull(candlesByAssetId);

        Map<UUID, List<MinuteCandle>> copiedCandlesByAssetId = new LinkedHashMap<>();

        for (Map.Entry<UUID, List<MinuteCandle>> entry : candlesByAssetId.entrySet()) {
            UUID assetId = Objects.requireNonNull(entry.getKey());
            List<MinuteCandle> candles = List.copyOf(entry.getValue());

            validateCandles(
                    assetId,
                    strategyDsl,
                    evaluationAt,
                    candles
            );
            copiedCandlesByAssetId.put(assetId, candles);
        }

        return Collections.unmodifiableMap(copiedCandlesByAssetId);
    }

    private static void validateCandles(
            UUID assetId,
            StrategyDsl strategyDsl,
            Instant evaluationAt,
            List<MinuteCandle> candles
    ) {
        int intervalMinutes = strategyDsl.timeframe().getIntervalMinutes();
        Duration candleDuration = Duration.ofMinutes(intervalMinutes);
        Instant previousOpenTime = null;

        for (MinuteCandle candle : candles) {
            if (!assetId.equals(candle.getId().getAssetId())) {
                throw new IllegalArgumentException(
                        "Candle asset must match map key"
                );
            }

            if (candle.getId().getIntervalMinutes() != intervalMinutes) {
                throw new IllegalArgumentException(
                        "Candle interval must match strategy timeframe"
                );
            }

            Instant openTime = candle.getId().getOpenTime();
            if (!openTime.plus(candleDuration).isBefore(evaluationAt)
                    && !openTime.plus(candleDuration).equals(evaluationAt)) {
                throw new IllegalArgumentException(
                        "Candle must be closed at evaluation time"
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
