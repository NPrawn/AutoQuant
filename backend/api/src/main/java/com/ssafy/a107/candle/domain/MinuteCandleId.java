package com.ssafy.a107.candle.domain;

import com.ssafy.a107.common.exception.ApiErrorCode;
import com.ssafy.a107.common.exception.ApiException;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

@Embeddable
public class MinuteCandleId implements Serializable {

    @Column(name = "market_id", nullable = false)
    private Long marketId;

    @Column(name = "interval_minutes", nullable = false)
    private short intervalMinutes;

    @Column(name = "open_time", nullable = false)
    private Instant openTime;

    protected MinuteCandleId() {
    }

    public MinuteCandleId(Long marketId, int intervalMinutes, Instant openTime) {
        if (intervalMinutes <= 0 || intervalMinutes > Short.MAX_VALUE) {
            throw new ApiException(ApiErrorCode.CANDLE_INVALID_INTERVAL);
        }

        this.marketId = Objects.requireNonNull(marketId);
        this.intervalMinutes = (short) intervalMinutes;
        this.openTime = Objects.requireNonNull(openTime);
    }

    public Long getMarketId() {
        return marketId;
    }

    public short getIntervalMinutes() {
        return intervalMinutes;
    }

    public Instant getOpenTime() {
        return openTime;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof MinuteCandleId that)) {
            return false;
        }
        return intervalMinutes == that.intervalMinutes
                && Objects.equals(marketId, that.marketId)
                && Objects.equals(openTime, that.openTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(marketId, intervalMinutes, openTime);
    }
}
