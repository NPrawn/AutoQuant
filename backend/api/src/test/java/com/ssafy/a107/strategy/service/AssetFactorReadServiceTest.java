package com.ssafy.a107.strategy.service;

import com.ssafy.a107.candle.domain.MinuteCandle;
import com.ssafy.a107.candle.domain.MinuteCandleFixture;
import com.ssafy.a107.candle.service.MinuteCandleReadService;
import com.ssafy.a107.strategy.domain.dsl.FactorType;
import com.ssafy.a107.strategy.domain.dsl.Timeframe;
import com.ssafy.a107.strategy.engine.factor.Ta4jFactorCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class AssetFactorReadServiceTest {

    private static final UUID ASSET_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant FIRST_OPEN_TIME = Instant.parse("2026-01-01T00:00:00Z");

    @Mock
    private MinuteCandleReadService candleReadService;

    private final Ta4jFactorCalculator factorCalculator = new Ta4jFactorCalculator();

    private AssetFactorReadService service() {
        return new AssetFactorReadService(candleReadService, factorCalculator);
    }

    @Test
    @DisplayName("여섯 팩터를 서버가 정한 기간으로 계산해 정해진 순서로 반환한다")
    void findCurrent() {
        given(candleReadService.findRecentCandles(eq(ASSET_ID), eq(1440), anyInt()))
                .willReturn(candles(1440, 62));

        AssetFactorSnapshot snapshot = service().findCurrent(ASSET_ID, Timeframe.ONE_DAY);

        assertThat(snapshot.values())
                .extracting(AssetFactorValue::factor, AssetFactorValue::period)
                .containsExactly(
                        tuple(FactorType.RSI, 14),
                        tuple(FactorType.RETURN, 20),
                        tuple(FactorType.SMA_DISPARITY, 20),
                        tuple(FactorType.VOLATILITY, 20),
                        tuple(FactorType.VOLUME_RATIO, 20),
                        tuple(FactorType.DRAWDOWN, 60));
        assertThat(snapshot.values()).extracting(AssetFactorValue::value).doesNotContainNull();
    }

    @Test
    @DisplayName("기간 최댓값을 채울 만큼 봉을 읽고 마지막 봉 시각을 함께 반환한다")
    void readEnoughCandles() {
        List<MinuteCandle> candles = candles(1440, 62);
        given(candleReadService.findRecentCandles(eq(ASSET_ID), eq(1440), anyInt())).willReturn(candles);

        AssetFactorSnapshot snapshot = service().findCurrent(ASSET_ID, Timeframe.ONE_DAY);

        then(candleReadService).should().findRecentCandles(ASSET_ID, 1440, 62);
        assertThat(snapshot.asOf()).isEqualTo(candles.getLast().getId().getOpenTime());
    }

    @ParameterizedTest
    @CsvSource({"1H,60", "4H,240", "1D,1440"})
    @DisplayName("봉 주기에 해당하는 간격의 봉을 읽는다")
    void readTimeframeInterval(String timeframe, int intervalMinutes) {
        given(candleReadService.findRecentCandles(eq(ASSET_ID), eq(intervalMinutes), anyInt()))
                .willReturn(candles(intervalMinutes, 62));

        service().findCurrent(ASSET_ID, Timeframe.fromValue(timeframe));

        then(candleReadService).should().findRecentCandles(ASSET_ID, intervalMinutes, 62);
    }

    @Test
    @DisplayName("봉이 기간을 채우지 못하면 그 팩터만 값을 비우고 오류로 만들지 않는다")
    void insufficientCandles() {
        given(candleReadService.findRecentCandles(eq(ASSET_ID), eq(1440), anyInt()))
                .willReturn(candles(1440, 16));

        AssetFactorSnapshot snapshot = service().findCurrent(ASSET_ID, Timeframe.ONE_DAY);

        assertThat(snapshot.values())
                .filteredOn(value -> value.value() == null)
                .extracting(AssetFactorValue::factor)
                .containsExactly(FactorType.RETURN, FactorType.SMA_DISPARITY,
                        FactorType.VOLATILITY, FactorType.VOLUME_RATIO, FactorType.DRAWDOWN);
        assertThat(snapshot.values().getFirst().value()).isNotNull();
    }

    @Test
    @DisplayName("봉이 없으면 모든 값과 기준 시각이 비어 있다")
    void withoutCandles() {
        given(candleReadService.findRecentCandles(eq(ASSET_ID), eq(1440), anyInt())).willReturn(List.of());

        AssetFactorSnapshot snapshot = service().findCurrent(ASSET_ID, Timeframe.ONE_DAY);

        assertThat(snapshot.asOf()).isNull();
        assertThat(snapshot.values()).hasSize(6).allMatch(value -> value.value() == null);
    }

    private List<MinuteCandle> candles(int intervalMinutes, int count) {
        List<MinuteCandle> candles = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            candles.add(MinuteCandleFixture.minuteCandle(
                    ASSET_ID,
                    intervalMinutes,
                    FIRST_OPEN_TIME.plus(Duration.ofMinutes((long) intervalMinutes * index)),
                    BigDecimal.valueOf(100 + index),
                    BigDecimal.valueOf(10 + index)));
        }
        return candles;
    }
}
