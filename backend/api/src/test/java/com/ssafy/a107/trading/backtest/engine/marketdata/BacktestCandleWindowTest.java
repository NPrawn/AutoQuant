package com.ssafy.a107.trading.backtest.engine.marketdata;

import com.ssafy.a107.candle.domain.MinuteCandle;
import com.ssafy.a107.candle.domain.MinuteCandleFixture;
import com.ssafy.a107.strategy.domain.dsl.Timeframe;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BacktestCandleWindowTest {

    private static final UUID ASSET_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID OTHER_ASSET_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final Instant EVALUATION_START = Instant.parse("2026-01-02T00:00:00Z");
    private static final Instant EVALUATION_END = Instant.parse("2026-01-03T00:00:00Z");

    @DisplayName("Warm-up 캔들과 평가 캔들을 평가 시작 시각으로 분리한다")
    @Test
    void splitWarmupAndEvaluationCandles() {
        List<MinuteCandle> candles = new ArrayList<>(List.of(
                candle(Instant.parse("2026-01-01T23:00:00Z")),
                candle(EVALUATION_START),
                candle(Instant.parse("2026-01-02T01:00:00Z"))
        ));

        BacktestCandleWindow window = new BacktestCandleWindow(
                Timeframe.ONE_HOUR,
                EVALUATION_START,
                EVALUATION_END,
                Map.of(ASSET_ID, candles)
        );
        candles.clear();

        assertThat(window.warmupCandlesByAssetId().get(ASSET_ID))
                .extracting(candle -> candle.getId().getOpenTime())
                .containsExactly(Instant.parse("2026-01-01T23:00:00Z"));
        assertThat(window.evaluationCandlesByAssetId().get(ASSET_ID))
                .extracting(candle -> candle.getId().getOpenTime())
                .containsExactly(
                        EVALUATION_START,
                        Instant.parse("2026-01-02T01:00:00Z")
                );
        assertThatThrownBy(() -> window.candlesByAssetId().put(OTHER_ASSET_ID, List.of()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @DisplayName("캔들 Timeframe이 백테스트 Timeframe과 다르면 거절한다")
    @Test
    void rejectCandleWithDifferentTimeframe() {
        MinuteCandle fourHourCandle = MinuteCandleFixture.minuteCandle(
                ASSET_ID,
                Timeframe.FOUR_HOURS.getIntervalMinutes(),
                Instant.parse("2026-01-01T20:00:00Z"),
                BigDecimal.ONE,
                BigDecimal.ONE
        );

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new BacktestCandleWindow(
                        Timeframe.ONE_HOUR,
                        EVALUATION_START,
                        EVALUATION_END,
                        Map.of(ASSET_ID, List.of(fourHourCandle))
                ))
                .withMessage("Candle interval must match timeframe");
    }

    @DisplayName("캔들은 오름차순 open time으로 정렬되어야 한다")
    @Test
    void rejectUnorderedCandles() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new BacktestCandleWindow(
                        Timeframe.ONE_HOUR,
                        EVALUATION_START,
                        EVALUATION_END,
                        Map.of(ASSET_ID, List.of(
                                candle(Instant.parse("2026-01-02T01:00:00Z")),
                                candle(EVALUATION_START)
                        ))
                ))
                .withMessage("Candles must be ordered by ascending open time");
    }

    @DisplayName("캔들 종목이 Map 키와 다르면 거절한다")
    @Test
    void rejectCandleForDifferentAsset() {
        MinuteCandle otherAssetCandle = MinuteCandleFixture.minuteCandle(
                OTHER_ASSET_ID,
                Timeframe.ONE_HOUR.getIntervalMinutes(),
                Instant.parse("2026-01-01T23:00:00Z"),
                BigDecimal.ONE,
                BigDecimal.ONE
        );

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new BacktestCandleWindow(
                        Timeframe.ONE_HOUR,
                        EVALUATION_START,
                        EVALUATION_END,
                        Map.of(ASSET_ID, List.of(otherAssetCandle))
                ))
                .withMessage("Candle asset must match map key");
    }

    private MinuteCandle candle(Instant openTime) {
        return MinuteCandleFixture.minuteCandle(
                ASSET_ID,
                Timeframe.ONE_HOUR.getIntervalMinutes(),
                openTime,
                BigDecimal.ONE,
                BigDecimal.ONE
        );
    }
}
