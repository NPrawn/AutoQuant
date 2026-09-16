package com.ssafy.a107.contest.domain;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.Objects;
import java.util.UUID;

/*
 * 데이터셋에 들어 있는 종목이다. 대회 백테스트의 후보군은 이 목록이고 거래지원 기간은 보지 않는다 (ADR-0014).
 */
@Entity
@Table(name = "market_dataset_asset", schema = "contest")
public class MarketDatasetAsset {

    @EmbeddedId
    private MarketDatasetAssetId id;

    protected MarketDatasetAsset() {
    }

    public MarketDatasetAsset(UUID datasetId, UUID assetId) {
        this.id = new MarketDatasetAssetId(datasetId, assetId);
    }

    public MarketDatasetAssetId getId() {
        return id;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof MarketDatasetAsset that)) {
            return false;
        }
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
