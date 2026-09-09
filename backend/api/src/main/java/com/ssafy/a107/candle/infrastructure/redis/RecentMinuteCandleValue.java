package com.ssafy.a107.candle.infrastructure.redis;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.ssafy.a107.candle.domain.MinuteCandleSnapshot;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
record RecentMinuteCandleValue(
        @NotNull UUID assetId,
        @NotNull @Min(1) @Max(1440) Integer intervalMinutes,
        @NotNull @Positive Long openTimeUnixMilli,
        @NotNull @DecimalMin("0") String openPrice,
        @NotNull @DecimalMin("0") String highPrice,
        @NotNull @DecimalMin("0") String lowPrice,
        @NotNull @DecimalMin("0") String closePrice,
        @NotNull @DecimalMin("0") String tradeVolume,
        @NotNull @DecimalMin("0") String tradeAmount,
        @NotNull @Positive Long lastEventTimeUnixMilli
) {
    MinuteCandleSnapshot toSnapshot() {
        return new MinuteCandleSnapshot(assetId, openTimeUnixMilli, openPrice, highPrice,
                lowPrice, closePrice, tradeVolume, tradeAmount, lastEventTimeUnixMilli);
    }

    static RecentMinuteCandleValue from(MinuteCandleSnapshot candle, int intervalMinutes) {
        return new RecentMinuteCandleValue(candle.assetId(), intervalMinutes, candle.openTimeUnixMilli(),
                candle.openPrice(), candle.highPrice(), candle.lowPrice(), candle.closePrice(),
                candle.tradeVolume(), candle.tradeAmount(), candle.lastEventTimeUnixMilli());
    }
}
