package com.ssafy.a107.trading.backtest.engine.marketdata;

import com.ssafy.a107.candle.domain.MinuteCandle;
import com.ssafy.a107.candle.domain.MinuteCandleId;
import com.ssafy.a107.strategy.domain.dsl.Timeframe;
import com.ssafy.a107.trading.backtest.repository.MinuteCandleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class JpaHistoricalCandleProviderTest {

    private static final UUID ASSET_A_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ASSET_B_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final Instant FROM = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-01-02T00:00:00Z");

    @Mock
    private MinuteCandleRepository minuteCandleRepository;

    @Mock
    private MinuteCandle candle;

    @Mock
    private MinuteCandleId candleId;

    @DisplayName("요청 종목별 과거 캔들을 시간 구간과 Timeframe 기준으로 조회한다")
    @Test
    void loadHistoricalCandlesByAssetAndTimeframe() {
        given(minuteCandleRepository
                .findAllByIdAssetIdInAndIdIntervalMinutesAndIdOpenTimeGreaterThanEqualAndIdOpenTimeLessThanOrderByIdAssetIdAscIdOpenTimeAsc(
                        List.of(ASSET_A_ID, ASSET_B_ID),
                        (short) Timeframe.ONE_HOUR.getIntervalMinutes(),
                        FROM,
                        TO
                ))
                .willReturn(List.of(candle));
        given(candle.getId()).willReturn(candleId);
        given(candleId.getAssetId()).willReturn(ASSET_B_ID);

        Map<UUID, List<MinuteCandle>> candlesByAssetId = provider().load(
                List.of(ASSET_B_ID, ASSET_A_ID, ASSET_B_ID),
                Timeframe.ONE_HOUR,
                FROM,
                TO
        );

        assertThat(candlesByAssetId.keySet()).containsExactly(ASSET_A_ID, ASSET_B_ID);
        assertThat(candlesByAssetId.get(ASSET_A_ID)).isEmpty();
        assertThat(candlesByAssetId.get(ASSET_B_ID)).containsExactly(candle);
        assertThatThrownBy(() -> candlesByAssetId.put(UUID.randomUUID(), List.of()))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> candlesByAssetId.get(ASSET_B_ID).add(candle))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @DisplayName("종료 시각이 시작 시각보다 빠르거나 같으면 캔들을 조회하지 않는다")
    @Test
    void rejectInvalidCandleRange() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> provider().load(
                        List.of(ASSET_A_ID),
                        Timeframe.ONE_HOUR,
                        FROM,
                        FROM
                ))
                .withMessage("Candle range start must be before end");

        then(minuteCandleRepository).shouldHaveNoInteractions();
    }

    @DisplayName("요청 종목이 없으면 저장소를 조회하지 않고 빈 결과를 반환한다")
    @Test
    void returnEmptyResultWithoutRequestedAssets() {
        Map<UUID, List<MinuteCandle>> candlesByAssetId = provider().load(
                List.of(),
                Timeframe.ONE_HOUR,
                FROM,
                TO
        );

        assertThat(candlesByAssetId).isEmpty();
        then(minuteCandleRepository).shouldHaveNoInteractions();
    }

    private JpaHistoricalCandleProvider provider() {
        return new JpaHistoricalCandleProvider(minuteCandleRepository);
    }
}
