package com.ssafy.a107.candle.domain;

import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class MinuteCandleFixture {

    private MinuteCandleFixture() {
    }

    public static MinuteCandle minuteCandle(
            UUID assetId,
            int intervalMinutes,
            Instant openTime,
            BigDecimal closePrice,
            BigDecimal tradeVolume
    ) {
        MinuteCandle candle = new MinuteCandle();
        ReflectionTestUtils.setField(candle, "id", new MinuteCandleId(assetId, intervalMinutes, openTime));
        ReflectionTestUtils.setField(candle, "openPrice", closePrice);
        ReflectionTestUtils.setField(candle, "highPrice", closePrice);
        ReflectionTestUtils.setField(candle, "lowPrice", closePrice);
        ReflectionTestUtils.setField(candle, "closePrice", closePrice);
        ReflectionTestUtils.setField(candle, "tradeVolume", tradeVolume);
        ReflectionTestUtils.setField(candle, "tradeAmount", closePrice.multiply(tradeVolume));
        ReflectionTestUtils.setField(candle, "lastEventTime", openTime.plusSeconds(intervalMinutes * 60L));
        return candle;
    }
}
