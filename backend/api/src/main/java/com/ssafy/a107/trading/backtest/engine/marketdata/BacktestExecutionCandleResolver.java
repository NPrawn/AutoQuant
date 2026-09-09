package com.ssafy.a107.trading.backtest.engine.marketdata;

import com.ssafy.a107.candle.domain.MinuteCandle;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Component
public class BacktestExecutionCandleResolver {

    public Optional<MinuteCandle> findNextExecutionCandle(
            BacktestCandleWindow candleWindow,
            UUID assetId,
            Instant signalCandleOpenTime
    ) {
        Objects.requireNonNull(candleWindow);
        Objects.requireNonNull(assetId);
        Objects.requireNonNull(signalCandleOpenTime);

        Instant executionCandleOpenTime = signalCandleOpenTime.plus(
                Duration.ofMinutes(candleWindow.timeframe().getIntervalMinutes())
        );

        return findCandleAt(
                candleWindow.candlesByAssetId().get(assetId),
                executionCandleOpenTime
        );
    }

    public Map<UUID, BigDecimal> resolveClosePrices(
            BacktestCandleWindow candleWindow,
            Instant candleOpenTime
    ) {
        Objects.requireNonNull(candleWindow);
        Objects.requireNonNull(candleOpenTime);

        Map<UUID, BigDecimal> closePrices = new LinkedHashMap<>();

        for (Map.Entry<UUID, List<MinuteCandle>> entry
                : candleWindow.candlesByAssetId().entrySet()) {
            findCandleAt(entry.getValue(), candleOpenTime)
                    .ifPresent(candle -> closePrices.put(
                            entry.getKey(),
                            candle.getClosePrice()
                    ));
        }

        return Map.copyOf(closePrices);
    }

    private Optional<MinuteCandle> findCandleAt(
            List<MinuteCandle> candles,
            Instant candleOpenTime
    ) {
        if (candles == null) {
            return Optional.empty();
        }

        return candles.stream()
                .filter(candle -> candle.getId().getOpenTime()
                        .equals(candleOpenTime))
                .findFirst();
    }
}
