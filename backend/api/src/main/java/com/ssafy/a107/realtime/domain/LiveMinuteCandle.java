package com.ssafy.a107.realtime.domain;

import java.util.UUID;

public record LiveMinuteCandle(
        UUID assetId, String marketCode, int intervalMinutes, long openTimeUnixMilli,
        String openPrice, String highPrice, String lowPrice, String closePrice,
        String tradeVolume, String tradeAmount, long lastEventTimeUnixMilli, long lastSequenceId
) {
}
