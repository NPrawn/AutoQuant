package com.ssafy.a107.contest.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class ContestCandleId implements Serializable {

    @Column(name = "dataset_id", nullable = false)
    private UUID datasetId;

    @Column(name = "asset_id", nullable = false)
    private UUID assetId;

    @Column(name = "interval_minutes", nullable = false)
    private short intervalMinutes;

    @Column(name = "open_time", nullable = false)
    private Instant openTime;

    protected ContestCandleId() {
    }

    public ContestCandleId(UUID datasetId, UUID assetId, int intervalMinutes, Instant openTime) {
        if (intervalMinutes <= 0 || intervalMinutes > Short.MAX_VALUE) {
            throw new IllegalArgumentException("Interval minutes must be between 1 and " + Short.MAX_VALUE);
        }

        this.datasetId = Objects.requireNonNull(datasetId);
        this.assetId = Objects.requireNonNull(assetId);
        this.intervalMinutes = (short) intervalMinutes;
        this.openTime = Objects.requireNonNull(openTime);
    }

    public UUID getDatasetId() {
        return datasetId;
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
        if (!(object instanceof ContestCandleId that)) {
            return false;
        }
        return intervalMinutes == that.intervalMinutes
                && Objects.equals(datasetId, that.datasetId)
                && Objects.equals(assetId, that.assetId)
                && Objects.equals(openTime, that.openTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(datasetId, assetId, intervalMinutes, openTime);
    }
}
