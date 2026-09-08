package com.ssafy.a107.realtime.infrastructure.redis;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.ssafy.a107.realtime.domain.RealtimeTrade;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
record RealtimeTradeMessage(
        @NotNull @Pattern(regexp = RedisMessageFormat.UUID_PATTERN) String assetId,
        @NotNull @Pattern(regexp = RedisMessageFormat.MARKET_CODE_PATTERN) String marketCode,
        @NotNull @DecimalMin(value = "0", inclusive = false) String price,
        @NotNull @DecimalMin(value = "0", inclusive = false) String volume,
        @NotNull @Pattern(regexp = "ASK|BID") String side,
        @NotNull @Positive Long tradedAtUnixMilli,
        @NotNull @Positive Long sequenceId
) {
    RealtimeTrade toEvent() {
        return new RealtimeTrade(UUID.fromString(assetId), marketCode, price, volume, side,
                tradedAtUnixMilli, sequenceId);
    }
}
