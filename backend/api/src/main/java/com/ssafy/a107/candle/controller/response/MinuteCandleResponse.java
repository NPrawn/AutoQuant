package com.ssafy.a107.candle.controller.response;

import com.ssafy.a107.candle.domain.MinuteCandleSnapshot;

public record MinuteCandleResponse(
        long openTimeUnixMilli,
        String openPrice, String highPrice, String lowPrice, String closePrice,
        String tradeVolume, String tradeAmount, long lastEventTimeUnixMilli
) {
    public static MinuteCandleResponse from(MinuteCandleSnapshot candle) {
        return new MinuteCandleResponse(candle.openTimeUnixMilli(), candle.openPrice(), candle.highPrice(),
                candle.lowPrice(), candle.closePrice(), candle.tradeVolume(), candle.tradeAmount(),
                candle.lastEventTimeUnixMilli());
    }
}
