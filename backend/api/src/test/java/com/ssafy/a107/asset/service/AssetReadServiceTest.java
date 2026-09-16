package com.ssafy.a107.asset.service;

import com.ssafy.a107.asset.domain.Asset;
import com.ssafy.a107.asset.domain.Exchange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class AssetReadServiceTest {

    @Mock
    private CollectedAssetReadService collectedAssetReadService;

    @Test
    @DisplayName("Upbit 원화 시장에서 거래지원 중이면서 수집하는 종목만 조회한다")
    void readCollectedTradingWonAssets() {
        Asset collected = mock(Asset.class);
        given(collectedAssetReadService.findCollectedTradingAssets(Exchange.UPBIT, "KRW"))
                .willReturn(List.of(collected));

        assertThat(new AssetReadService(collectedAssetReadService).findChartAssets())
                .containsExactly(collected);

        then(collectedAssetReadService).should().findCollectedTradingAssets(Exchange.UPBIT, "KRW");
    }

    @Test
    @DisplayName("수집하는 종목이 없으면 빈 목록을 반환한다")
    void readEmptyWhenNothingCollected() {
        given(collectedAssetReadService.findCollectedTradingAssets(Exchange.UPBIT, "KRW")).willReturn(List.of());

        assertThat(new AssetReadService(collectedAssetReadService).findChartAssets()).isEmpty();
    }
}
