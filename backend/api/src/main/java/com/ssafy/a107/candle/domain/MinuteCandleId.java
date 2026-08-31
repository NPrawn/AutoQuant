package com.ssafy.a107.candle.domain;

import com.ssafy.a107.common.exception.ApiErrorCode;
import com.ssafy.a107.common.exception.ApiException;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class MinuteCandleId implements Serializable {

    @Column(name = "asset_id", nullable = false)
    private UUID assetId;

    @Column(name = "interval_minutes", nullable = false)
    private short intervalMinutes;

    @Column(name = "open_time", nullable = false)
    private Instant openTime;

    protected MinuteCandleId() {
    }

    public MinuteCandleId(UUID assetId, int intervalMinutes, Instant openTime) {
        if (intervalMinutes <= 0 || intervalMinutes > Short.MAX_VALUE) {
            throw new ApiException(ApiErrorCode.CANDLE_INVALID_INTERVAL);
        }

        this.assetId = Objects.requireNonNull(assetId);
        this.intervalMinutes = (short) intervalMinutes;
        this.openTime = Objects.requireNonNull(openTime);
    }

    public UUID getAssetId() {
        return assetId;
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
                && Objects.equals(assetId, that.assetId)
                && Objects.equals(openTime, that.openTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(assetId, intervalMinutes, openTime);
    }
}
