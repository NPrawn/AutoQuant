package com.ssafy.a107.trading.backtest.engine.marketdata;

import com.ssafy.a107.candle.domain.MinuteCandle;
import com.ssafy.a107.strategy.domain.dsl.Timeframe;
import com.ssafy.a107.trading.backtest.repository.MinuteCandleRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

@Component
public class JpaHistoricalCandleProvider implements HistoricalCandleProvider {

    private final MinuteCandleRepository minuteCandleRepository;

    public JpaHistoricalCandleProvider(
            MinuteCandleRepository minuteCandleRepository
    ) {
        this.minuteCandleRepository = minuteCandleRepository;
    }

    @Override
    public Map<UUID, List<MinuteCandle>> load(
            Collection<UUID> assetIds,
            Timeframe timeframe,
            Instant fromInclusive,
            Instant toExclusive
    ) {
        Objects.requireNonNull(assetIds);
        Objects.requireNonNull(timeframe);
        Objects.requireNonNull(fromInclusive);
        Objects.requireNonNull(toExclusive);

        if (!fromInclusive.isBefore(toExclusive)) {
            throw new IllegalArgumentException(
                    "Candle range start must be before end"
            );
        }

        List<UUID> orderedAssetIds = assetIds.stream()
                .map(Objects::requireNonNull)
                .distinct()
                .sorted()
                .toList();

        if (orderedAssetIds.isEmpty()) {
            return Map.of();
        }

        Map<UUID, List<MinuteCandle>> candlesByAssetId = new LinkedHashMap<>();
        for (UUID assetId : orderedAssetIds) {
            candlesByAssetId.put(assetId, new ArrayList<>());
        }

        List<MinuteCandle> candles = minuteCandleRepository
                .findAllByIdAssetIdInAndIdIntervalMinutesAndIdOpenTimeGreaterThanEqualAndIdOpenTimeLessThanOrderByIdAssetIdAscIdOpenTimeAsc(
                        orderedAssetIds,
                        (short) timeframe.getIntervalMinutes(),
                        fromInclusive,
                        toExclusive
                );

        for (MinuteCandle candle : candles) {
            candlesByAssetId.get(candle.getId().getAssetId()).add(candle);
        }

        Map<UUID, List<MinuteCandle>> immutableCandlesByAssetId = new LinkedHashMap<>();
        for (Map.Entry<UUID, List<MinuteCandle>> entry : candlesByAssetId.entrySet()) {
            immutableCandlesByAssetId.put(entry.getKey(), List.copyOf(entry.getValue()));
        }

        return Collections.unmodifiableMap(immutableCandlesByAssetId);
    }
}
