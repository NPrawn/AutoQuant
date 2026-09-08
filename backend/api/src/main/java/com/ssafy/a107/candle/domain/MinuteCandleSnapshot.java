package com.ssafy.a107.candle.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record MinuteCandleSnapshot(
        UUID assetId, long openTimeUnixMilli,
        String openPrice, String highPrice, String lowPrice, String closePrice,
        String tradeVolume, String tradeAmount, long lastEventTimeUnixMilli
) {
    public static MinuteCandleSnapshot from(MinuteCandle candle) {
        return new MinuteCandleSnapshot(
                candle.getId().getAssetId(), candle.getId().getOpenTime().toEpochMilli(),
                decimal(candle.getOpenPrice()), decimal(candle.getHighPrice()),
                decimal(candle.getLowPrice()), decimal(candle.getClosePrice()),
                decimal(candle.getTradeVolume()), decimal(candle.getTradeAmount()),
                candle.getLastEventTime().toEpochMilli()
        );
    }

    private static String decimal(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
