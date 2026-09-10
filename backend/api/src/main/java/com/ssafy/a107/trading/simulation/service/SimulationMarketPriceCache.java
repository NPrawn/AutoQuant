package com.ssafy.a107.trading.simulation.service;

import com.ssafy.a107.realtime.domain.LiveMinuteCandle;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SimulationMarketPriceCache {

    private final Map<PriceKey, PriceSnapshot> pricesByKey = new ConcurrentHashMap<>();

    public void update(LiveMinuteCandle candle) {
        pricesByKey.put(new PriceKey(candle.assetId(), candle.intervalMinutes()), new PriceSnapshot(
                Instant.ofEpochMilli(candle.openTimeUnixMilli()),
                new BigDecimal(candle.openPrice())
        ));
    }

    public Optional<Map<UUID, BigDecimal>> snapshotAt(Collection<UUID> assetIds, int intervalMinutes, Instant candleAt) {
        Map<UUID, BigDecimal> prices = new LinkedHashMap<>();
        for (UUID assetId : assetIds) {
            PriceSnapshot priceSnapshot = pricesByKey.get(new PriceKey(assetId, intervalMinutes));
            if (priceSnapshot == null || !priceSnapshot.candleOpenTime().equals(candleAt)) {
                return Optional.empty();
            }
            prices.put(assetId, priceSnapshot.openPrice());
        }
        return Optional.of(Map.copyOf(prices));
    }

    private record PriceSnapshot(Instant candleOpenTime, BigDecimal openPrice) {
    }

    private record PriceKey(UUID assetId, int intervalMinutes) {
    }
}
