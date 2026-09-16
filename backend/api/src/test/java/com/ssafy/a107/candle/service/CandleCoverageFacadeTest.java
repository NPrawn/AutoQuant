package com.ssafy.a107.candle.service;

import com.ssafy.a107.asset.service.CollectedAssetReadService;
import com.ssafy.a107.candle.domain.MinuteCandleCoverage;
import com.ssafy.a107.candle.repository.MinuteCandleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

class CandleCoverageFacadeTest {

    private static final UUID FIRST_ASSET_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SECOND_ASSET_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private final CollectedAssetReadService collectedAssetReadService = mock(CollectedAssetReadService.class);
    private final CandleCoverageReadService candleCoverageReadService = mock(CandleCoverageReadService.class);
    private final CandleCoverageFacade facade =
            new CandleCoverageFacade(collectedAssetReadService, candleCoverageReadService);

    @DisplayName("수집 종목만 대상으로 보유 구간을 구한다")
    @Test
    void findCoverageOfCollectedAssets() {
        Instant oldest = Instant.parse("2018-01-01T00:00:00Z");
        Instant newest = Instant.parse("2026-09-16T00:00:00Z");
        List<UUID> collected = List.of(FIRST_ASSET_ID, SECOND_ASSET_ID);
        given(collectedAssetReadService.findCollectedAssetIds()).willReturn(collected);
        given(candleCoverageReadService.findCoverage(1440, collected))
                .willReturn(new MinuteCandleCoverage(oldest, newest));

        MinuteCandleCoverage coverage = facade.findCoverage(1440);

        assertThat(coverage.oldestOpenTime()).isEqualTo(oldest);
        assertThat(coverage.newestOpenTime()).isEqualTo(newest);
        then(candleCoverageReadService).should().findCoverage(1440, collected);
    }

    @DisplayName("수집 종목이 없으면 빈 구간을 돌려준다")
    @Test
    void emptyCoverageWithoutCollectedAssets() {
        given(collectedAssetReadService.findCollectedAssetIds()).willReturn(List.of());
        given(candleCoverageReadService.findCoverage(1440, List.of())).willReturn(MinuteCandleCoverage.empty());

        assertThat(facade.findCoverage(1440).isEmpty()).isTrue();
    }

    @DisplayName("대상 종목이 없으면 봉을 조회하지 않는다")
    @Test
    void skipQueryWithoutCollectedAssets() {
        MinuteCandleRepository minuteCandleRepository = mock(MinuteCandleRepository.class);
        CandleCoverageReadService readService = new CandleCoverageReadService(minuteCandleRepository);

        assertThat(readService.findCoverage(1440, List.of()).isEmpty()).isTrue();

        then(minuteCandleRepository).should(never())
                .findFirstByIdIntervalMinutesAndIdAssetIdInOrderByIdOpenTimeAsc(anyShort(), any());
        then(minuteCandleRepository).should(never())
                .findFirstByIdIntervalMinutesAndIdAssetIdInOrderByIdOpenTimeDesc(anyShort(), any());
    }

    @DisplayName("대상 종목에 봉이 하나도 없으면 마지막 봉을 다시 찾지 않는다")
    @Test
    void skipNewestLookupWhenNoCandleExists() {
        MinuteCandleRepository minuteCandleRepository = mock(MinuteCandleRepository.class);
        CandleCoverageReadService readService = new CandleCoverageReadService(minuteCandleRepository);
        List<UUID> collected = List.of(FIRST_ASSET_ID);
        given(minuteCandleRepository.findFirstByIdIntervalMinutesAndIdAssetIdInOrderByIdOpenTimeAsc(
                (short) 1440, collected)).willReturn(Optional.empty());

        assertThat(readService.findCoverage(1440, collected).isEmpty()).isTrue();

        then(minuteCandleRepository).should(never())
                .findFirstByIdIntervalMinutesAndIdAssetIdInOrderByIdOpenTimeDesc(anyShort(), any());
    }
}
