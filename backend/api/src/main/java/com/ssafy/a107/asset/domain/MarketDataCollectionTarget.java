package com.ssafy.a107.asset.domain;

import com.ssafy.a107.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "market_data_collection_target", schema = "ops")
public class MarketDataCollectionTarget extends BaseEntity {

    @Id
    @Column(name = "asset_id", nullable = false, updatable = false)
    private UUID assetId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "asset_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_market_data_collection_target_asset")
    )
    private Asset asset;

    @Enumerated(EnumType.STRING)
    @Column(name = "collection_status", nullable = false, length = 20)
    private MarketDataCollectionStatus collectionStatus;

    protected MarketDataCollectionTarget() {
    }

    public MarketDataCollectionTarget(Asset asset) {
        this.asset = Objects.requireNonNull(asset);
        this.collectionStatus = MarketDataCollectionStatus.ENABLED;
    }

    public void enableCollection() {
        collectionStatus = MarketDataCollectionStatus.ENABLED;
    }

    public void disableCollection() {
        collectionStatus = MarketDataCollectionStatus.DISABLED;
    }

    public UUID getAssetId() {
        return assetId;
    }

    public Asset getAsset() {
        return asset;
    }

    public MarketDataCollectionStatus getCollectionStatus() {
        return collectionStatus;
    }
}
