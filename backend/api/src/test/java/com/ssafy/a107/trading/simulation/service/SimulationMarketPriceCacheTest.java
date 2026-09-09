package com.ssafy.a107.trading.simulation.service;

import com.ssafy.a107.realtime.domain.LiveMinuteCandle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SimulationMarketPriceCacheTest {

    private static final UUID ASSET_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @DisplayName("현재 Candle 시점 이후 가격은 실행 가격으로 사용하지 않는다")
    @Test
    void doesNotUseFuturePrice() {
        SimulationMarketPriceCache cache = new SimulationMarketPriceCache();
        cache.update(candle(Instant.parse("2026-09-14T01:00:00Z"), "100"));

        assertThat(cache.snapshotAtOrBefore(List.of(ASSET_ID), Instant.parse("2026-09-14T00:00:00Z")))
                .isEmpty();
    }

    @DisplayName("동일하거나 이전 Candle의 시가만 가격 스냅샷으로 반환한다")
    @Test
    void returnsOpenPriceAtOrBeforeCandle() {
        SimulationMarketPriceCache cache = new SimulationMarketPriceCache();
        Instant candleAt = Instant.parse("2026-09-14T00:00:00Z");
        cache.update(candle(candleAt, "100"));

        assertThat(cache.snapshotAtOrBefore(List.of(ASSET_ID), candleAt))
                .hasValueSatisfying(prices ->
                        assertThat(prices.get(ASSET_ID)).isEqualByComparingTo(BigDecimal.valueOf(100))
                );
    }

    private LiveMinuteCandle candle(Instant candleAt, String openPrice) {
        return new LiveMinuteCandle(
                ASSET_ID, "KRW-BTC", 60, candleAt.toEpochMilli(), openPrice, openPrice, openPrice,
                openPrice, "1", openPrice, candleAt.toEpochMilli(), 1
        );
    }
}
