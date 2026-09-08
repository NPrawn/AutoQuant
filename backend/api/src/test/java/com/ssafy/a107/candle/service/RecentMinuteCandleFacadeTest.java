package com.ssafy.a107.candle.service;

import com.ssafy.a107.candle.RecentMinuteCandleFixtures;
import com.ssafy.a107.candle.domain.MinuteCandleSnapshot;
import com.ssafy.a107.candle.service.port.RecentMinuteCandleCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.stream.IntStream;

import static com.ssafy.a107.candle.RecentMinuteCandleFixtures.ASSET_ID;
import static com.ssafy.a107.candle.RecentMinuteCandleFixtures.candle;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class RecentMinuteCandleFacadeTest {

    @Mock
    private RecentMinuteCandleCache cache;
    @Mock
    private MinuteCandleReadService readService;
    private RecentMinuteCandleFacade facade;

    @BeforeEach
    void setUp() {
        facade = new RecentMinuteCandleFacade(cache, readService);
    }

    @Test
    @DisplayName("요청 개수만큼 캐시가 있으면 DB에 접근하지 않는다")
    void returnCacheHit() {
        List<MinuteCandleSnapshot> cached = List.of(candle(1), candle(2));
        given(cache.findRecent(ASSET_ID, 2)).willReturn(cached);

        assertThat(facade.findRecent(ASSET_ID, 2)).containsExactlyElementsOf(cached);
        then(readService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("캐시가 비면 DB의 최근 1000개를 채우고 요청한 최신 개수만 반환한다")
    void fillCacheAndSliceLatest() {
        List<MinuteCandleSnapshot> stored = IntStream.range(0, 1000)
                .mapToObj(RecentMinuteCandleFixtures::candle).toList();
        given(cache.findRecent(ASSET_ID, 2)).willReturn(List.of());
        given(readService.findRecent(ASSET_ID)).willReturn(stored);

        assertThat(facade.findRecent(ASSET_ID, 2)).containsExactly(candle(998), candle(999));
        then(cache).should().fillMissing(ASSET_ID, stored);
    }

    @Test
    @DisplayName("일부 캐시만 있으면 충분한 과거 봉을 DB에서 보충한다")
    void supplementPartialCache() {
        given(cache.findRecent(ASSET_ID, 3)).willReturn(List.of(candle(3)));
        List<MinuteCandleSnapshot> stored = List.of(candle(1), candle(2), candle(3));
        given(readService.findRecent(ASSET_ID)).willReturn(stored);

        assertThat(facade.findRecent(ASSET_ID, 3)).containsExactlyElementsOf(stored);
        then(cache).should().fillMissing(ASSET_ID, stored);
    }

    @Test
    @DisplayName("DB에도 봉이 없으면 빈 목록을 반환하고 빈 캐시를 저장하지 않는다")
    void returnEmptyHistory() {
        given(cache.findRecent(ASSET_ID, 1000)).willReturn(List.of());
        given(readService.findRecent(ASSET_ID)).willReturn(List.of());

        assertThat(facade.findRecent(ASSET_ID, 1000)).isEmpty();
        then(cache).should().findRecent(ASSET_ID, 1000);
        then(cache).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("요청보다 봉이 적으면 존재하는 봉만 반환한다")
    void returnShortHistory() {
        given(cache.findRecent(ASSET_ID, 1000)).willReturn(List.of());
        given(readService.findRecent(ASSET_ID)).willReturn(List.of(candle(0)));

        assertThat(facade.findRecent(ASSET_ID, 1000)).containsExactly(candle(0));
    }
}
