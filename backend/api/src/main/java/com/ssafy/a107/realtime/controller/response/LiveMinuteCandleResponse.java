package com.ssafy.a107.realtime.controller.response;

import com.ssafy.a107.realtime.domain.LiveMinuteCandle;

import java.util.UUID;

public record LiveMinuteCandleResponse(
        UUID assetId, String marketCode, int intervalMinutes, long openTimeUnixMilli,
        String openPrice, String highPrice, String lowPrice, String closePrice,
        String tradeVolume, String tradeAmount, long lastEventTimeUnixMilli, long lastSequenceId
) {
    public static LiveMinuteCandleResponse from(LiveMinuteCandle candle) {
        return new LiveMinuteCandleResponse(candle.assetId(), candle.marketCode(), candle.intervalMinutes(),
                candle.openTimeUnixMilli(), candle.openPrice(), candle.highPrice(), candle.lowPrice(),
                candle.closePrice(), candle.tradeVolume(), candle.tradeAmount(),
                candle.lastEventTimeUnixMilli(), candle.lastSequenceId());
    }
}
