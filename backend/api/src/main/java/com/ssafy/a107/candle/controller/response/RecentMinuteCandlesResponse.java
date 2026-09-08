package com.ssafy.a107.candle.controller.response;

import com.ssafy.a107.candle.domain.MinuteCandleSnapshot;

import java.util.List;
import java.util.UUID;

public record RecentMinuteCandlesResponse(UUID assetId, int intervalMinutes, List<MinuteCandleResponse> candles) {

    public static RecentMinuteCandlesResponse from(UUID assetId, List<MinuteCandleSnapshot> candles) {
        return new RecentMinuteCandlesResponse(assetId, 1, candles.stream().map(MinuteCandleResponse::from).toList());
    }
}
