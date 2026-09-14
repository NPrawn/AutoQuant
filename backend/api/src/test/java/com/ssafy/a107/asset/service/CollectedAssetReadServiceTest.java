package com.ssafy.a107.asset.service;

import com.ssafy.a107.asset.domain.Asset;
import com.ssafy.a107.asset.domain.Exchange;
import com.ssafy.a107.asset.domain.MarketDataCollectionStatus;
import com.ssafy.a107.asset.domain.MarketDataCollectionTarget;
import com.ssafy.a107.asset.repository.AssetRepository;
import com.ssafy.a107.asset.repository.MarketDataCollectionTargetRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class CollectedAssetReadServiceTest {

    private static final Instant EVALUATION_AT = Instant.parse("2026-09-14T00:00:00Z");
    private static final Instant PERIOD_START = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant PERIOD_END = Instant.parse("2026-09-01T00:00:00Z");

    @Mock
    private AssetRepository assetRepository;

    @Mock
    private MarketDataCollectionTargetRepository collectionTargetRepository;

    private CollectedAssetReadService service() {
        return new CollectedAssetReadService(assetRepository, collectionTargetRepository);
    }

    @Test
    @DisplayName("거래지원 중이어도 수집하지 않는 종목은 후보에서 뺀다")
    void keepOnlyCollectedAssets() {
        Asset collected = asset();
        Asset stopped = asset();
        given(assetRepository.findAllAvailableAt(Exchange.UPBIT, "KRW", EVALUATION_AT))
                .willReturn(List.of(collected, stopped));
        MarketDataCollectionTarget enabled = target(collected);
        given(collectionTargetRepository.findByCollectionStatus(MarketDataCollectionStatus.ENABLED))
                .willReturn(List.of(enabled));

        List<Asset> universe = service().findAvailableAt(Exchange.UPBIT, "KRW", EVALUATION_AT);

        assertThat(universe).containsExactly(collected);
    }

    @Test
    @DisplayName("기간 조회도 같은 규칙으로 거른다")
    void keepOnlyCollectedAssetsDuringPeriod() {
        Asset collected = asset();
        Asset stopped = asset();
        given(assetRepository.findAllAvailableDuring(Exchange.UPBIT, "KRW", PERIOD_START, PERIOD_END))
                .willReturn(List.of(stopped, collected));
        MarketDataCollectionTarget enabled = target(collected);
        given(collectionTargetRepository.findByCollectionStatus(MarketDataCollectionStatus.ENABLED))
                .willReturn(List.of(enabled));

        List<Asset> universe = service().findAvailableDuring(Exchange.UPBIT, "KRW", PERIOD_START, PERIOD_END);

        assertThat(universe).containsExactly(collected);
    }

    @Test
    @DisplayName("수집 중인 종목이 하나도 없으면 후보도 비어 있다")
    void emptyWhenNothingIsCollected() {
        Asset available = asset();
        given(assetRepository.findAllAvailableAt(any(), any(), any())).willReturn(List.of(available));
        given(collectionTargetRepository.findByCollectionStatus(MarketDataCollectionStatus.ENABLED))
                .willReturn(List.of());

        assertThat(service().findAvailableAt(Exchange.UPBIT, "KRW", EVALUATION_AT)).isEmpty();
    }

    /* Asset 은 영속 엔티티라 식별자만 필요한 이 검증에서는 흉내로 충분하다. */
    private Asset asset() {
        Asset asset = mock(Asset.class);
        given(asset.getAssetId()).willReturn(UUID.randomUUID());
        return asset;
    }

    private MarketDataCollectionTarget target(Asset asset) {
        /* 스터빙 중에 다른 흉내 객체를 부르면 Mockito 가 스터빙이 끝나지 않은 것으로 본다. */
        UUID assetId = asset.getAssetId();
        MarketDataCollectionTarget target = mock(MarketDataCollectionTarget.class);
        given(target.getAssetId()).willReturn(assetId);
        return target;
    }
}
