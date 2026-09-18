package com.ssafy.a107.contest.service;

import com.ssafy.a107.candle.domain.MinuteCandle;
import com.ssafy.a107.contest.domain.ContestCandle;
import com.ssafy.a107.contest.domain.ContestCandleId;
import com.ssafy.a107.contest.domain.MarketDatasetAsset;
import com.ssafy.a107.contest.repository.ContestCandleRepository;
import com.ssafy.a107.contest.repository.MarketDatasetAssetRepository;
import com.ssafy.a107.strategy.domain.dsl.Timeframe;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JpaContestDatasetMarketDataTest {

    private static final UUID DATASET_ID = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    private static final UUID FIRST_ASSET_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID SECOND_ASSET_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    private static final Instant OPEN_TIME = Instant.parse("2030-02-11T00:00:00Z");

    private final MarketDatasetAssetRepository assetRepository = mock(MarketDatasetAssetRepository.class);
    private final ContestCandleRepository candleRepository = mock(ContestCandleRepository.class);
    private final JpaContestDatasetMarketData marketData =
            new JpaContestDatasetMarketData(assetRepository, candleRepository);

    @DisplayName("데이터셋 종목은 저장된 순서대로 돌려준다")
    @Test
    void findDatasetAssetIds() {
        when(assetRepository.findAllByIdDatasetIdOrderByIdAssetIdAsc(DATASET_ID)).thenReturn(List.of(
                new MarketDatasetAsset(DATASET_ID, FIRST_ASSET_ID),
                new MarketDatasetAsset(DATASET_ID, SECOND_ASSET_ID)
        ));

        assertThat(marketData.findAssetIds(DATASET_ID)).containsExactly(FIRST_ASSET_ID, SECOND_ASSET_ID);
    }

    @DisplayName("대회 캔들을 엔진이 쓰는 분봉 타입으로 옮기고, 캔들이 없는 종목도 빈 목록으로 채운다")
    @Test
    void loadCandlesForEveryRequestedAsset() {
        when(candleRepository
                .findAllByIdDatasetIdAndIdAssetIdInAndIdIntervalMinutesAndIdOpenTimeGreaterThanEqualAndIdOpenTimeLessThanOrderByIdAssetIdAscIdOpenTimeAsc(
                        eq(DATASET_ID), any(), anyShort(), any(), any()))
                .thenReturn(List.of(contestCandle()));

        Map<UUID, List<MinuteCandle>> candles = marketData.loadCandles(
                DATASET_ID,
                List.of(SECOND_ASSET_ID, FIRST_ASSET_ID),
                Timeframe.ONE_HOUR,
                OPEN_TIME,
                OPEN_TIME.plusSeconds(3_600)
        );

        assertThat(candles).containsOnlyKeys(FIRST_ASSET_ID, SECOND_ASSET_ID);
        assertThat(candles.get(SECOND_ASSET_ID)).isEmpty();
        MinuteCandle candle = candles.get(FIRST_ASSET_ID).getFirst();
        assertThat(candle.getId().getAssetId()).isEqualTo(FIRST_ASSET_ID);
        assertThat(candle.getId().getIntervalMinutes()).isEqualTo((short) 60);
        assertThat(candle.getClosePrice()).isEqualByComparingTo("110");
        assertThat(candle.getLastEventTime()).isEqualTo(OPEN_TIME.plusSeconds(3_599));
    }

    @DisplayName("끝이 시작보다 앞서면 캔들을 읽지 않는다")
    @Test
    void rejectInvertedRange() {
        assertThatIllegalArgumentException().isThrownBy(() -> marketData.loadCandles(
                DATASET_ID,
                List.of(FIRST_ASSET_ID),
                Timeframe.ONE_HOUR,
                OPEN_TIME,
                OPEN_TIME
        ));
    }

    private static ContestCandle contestCandle() {
        return new ContestCandle(
                new ContestCandleId(DATASET_ID, FIRST_ASSET_ID, 60, OPEN_TIME),
                new BigDecimal("100"),
                new BigDecimal("120"),
                new BigDecimal("90"),
                new BigDecimal("110"),
                new BigDecimal("1.5"),
                new BigDecimal("165")
        );
    }
}
