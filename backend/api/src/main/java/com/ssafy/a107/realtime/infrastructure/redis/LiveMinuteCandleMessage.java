package com.ssafy.a107.realtime.infrastructure.redis;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.ssafy.a107.realtime.domain.LiveMinuteCandle;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
record LiveMinuteCandleMessage(
        @NotNull @Pattern(regexp = RedisMessageFormat.UUID_PATTERN) String assetId,
        @NotNull @Pattern(regexp = RedisMessageFormat.MARKET_CODE_PATTERN) String marketCode,
        @NotNull @Min(1) @Max(1) Integer intervalMinutes,
        @NotNull @Positive Long openTimeUnixMilli,
        @NotNull @DecimalMin(value = "0", inclusive = false) String openPrice,
        @NotNull @DecimalMin(value = "0", inclusive = false) String highPrice,
        @NotNull @DecimalMin(value = "0", inclusive = false) String lowPrice,
        @NotNull @DecimalMin(value = "0", inclusive = false) String closePrice,
        @NotNull @DecimalMin("0") String tradeVolume,
        @NotNull @DecimalMin("0") String tradeAmount,
        @NotNull @Positive Long lastEventTimeUnixMilli,
        @NotNull @Positive Long lastSequenceId
) {
    LiveMinuteCandle toEvent() {
        return new LiveMinuteCandle(UUID.fromString(assetId), marketCode, intervalMinutes, openTimeUnixMilli,
                openPrice, highPrice, lowPrice, closePrice, tradeVolume, tradeAmount,
                lastEventTimeUnixMilli, lastSequenceId);
    }
}
