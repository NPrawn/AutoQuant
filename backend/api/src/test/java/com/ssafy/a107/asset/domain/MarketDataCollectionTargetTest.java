package com.ssafy.a107.asset.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class MarketDataCollectionTargetTest {

    @DisplayName("수집 대상은 활성화된 상태로 생성된다")
    @Test
    void createEnabledCollectionTarget() {
        Asset asset = createBitcoinAsset();

        MarketDataCollectionTarget collectionTarget = new MarketDataCollectionTarget(asset);

        assertThat(collectionTarget.getAsset()).isSameAs(asset);
        assertThat(collectionTarget.getCollectionStatus())
                .isEqualTo(MarketDataCollectionStatus.ENABLED);
    }

    @DisplayName("수집 대상을 비활성화한 뒤 다시 활성화할 수 있다")
    @Test
    void changeCollectionStatus() {
        MarketDataCollectionTarget collectionTarget = new MarketDataCollectionTarget(createBitcoinAsset());

        collectionTarget.disableCollection();
        assertThat(collectionTarget.getCollectionStatus())
                .isEqualTo(MarketDataCollectionStatus.DISABLED);

        collectionTarget.enableCollection();
        assertThat(collectionTarget.getCollectionStatus())
                .isEqualTo(MarketDataCollectionStatus.ENABLED);
    }

    private Asset createBitcoinAsset() {
        return new Asset(
                Exchange.UPBIT,
                "KRW-BTC",
                "BTC",
                "KRW",
                "비트코인",
                "Bitcoin",
                Instant.parse("2017-09-25T00:00:00Z")
        );
    }
}
