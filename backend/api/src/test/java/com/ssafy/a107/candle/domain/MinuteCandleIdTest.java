package com.ssafy.a107.candle.domain;

import com.ssafy.a107.common.exception.ApiErrorCode;
import com.ssafy.a107.common.exception.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MinuteCandleIdTest {

    @DisplayName("거래 자산과 분봉 간격과 시작 시각이 같으면 동일한 식별자다")
    @Test
    void identifySameMinuteCandle() {
        Instant openTime = Instant.parse("2026-08-31T01:00:00Z");
        UUID assetId = UUID.fromString("46b0aa29-7ca2-4d04-a0a9-286b1b65eb87");
        MinuteCandleId first = new MinuteCandleId(assetId, 1, openTime);
        MinuteCandleId second = new MinuteCandleId(assetId, 1, openTime);

        assertThat(first).isEqualTo(second);
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
    }

    @DisplayName("유효하지 않은 분봉 간격이면 API 예외가 발생한다")
    @Test
    void rejectInvalidIntervalMinutes() {
        Instant openTime = Instant.parse("2026-08-31T01:00:00Z");

        UUID assetId = UUID.fromString("46b0aa29-7ca2-4d04-a0a9-286b1b65eb87");

        assertThatThrownBy(() -> new MinuteCandleId(assetId, 0, openTime))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ApiErrorCode.CANDLE_INVALID_INTERVAL)
                );
    }
}
