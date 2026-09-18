package com.ssafy.a107.contest.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class MarketDatasetAssetId implements Serializable {

    @Column(name = "dataset_id", nullable = false)
    private UUID datasetId;

    @Column(name = "asset_id", nullable = false)
    private UUID assetId;

    protected MarketDatasetAssetId() {
    }

    public MarketDatasetAssetId(UUID datasetId, UUID assetId) {
        this.datasetId = Objects.requireNonNull(datasetId);
        this.assetId = Objects.requireNonNull(assetId);
    }

    public UUID getDatasetId() {
        return datasetId;
    }

    public UUID getAssetId() {
        return assetId;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof MarketDatasetAssetId that)) {
            return false;
        }
        return Objects.equals(datasetId, that.datasetId) && Objects.equals(assetId, that.assetId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(datasetId, assetId);
    }
}
