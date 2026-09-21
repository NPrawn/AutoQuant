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

    @DisplayName("평가 종료 시각의 봉은 다음 체결 봉으로 보관하고 평가 봉에서는 제외한다")
    @Test
    void keepExecutionCandleAtEvaluationEnd() {
        BacktestCandleWindow window = new BacktestCandleWindow(
                Timeframe.ONE_HOUR,
                EVALUATION_START,
                EVALUATION_END,
                Map.of(ASSET_ID, List.of(
                        candle(Instant.parse("2026-01-02T23:00:00Z")),
                        candle(EVALUATION_END)
                ))
        );

        assertThat(window.candlesByAssetId().get(ASSET_ID))
                .extracting(candle -> candle.getId().getOpenTime())
                .containsExactly(
                        Instant.parse("2026-01-02T23:00:00Z"),
                        EVALUATION_END
                );
        assertThat(window.evaluationCandlesByAssetId().get(ASSET_ID))
                .extracting(candle -> candle.getId().getOpenTime())
                .containsExactly(Instant.parse("2026-01-02T23:00:00Z"));
    }

    /*
     * 화면은 종료일을 로컬 23:59:59.999(=14:59:59.999Z) 로 보내 봉 경계와 어긋난다. 종료일이 과거이면
     * 그 뒤에 열리는 봉이 이미 있어 적재되는데, 예전에는 이 봉을 거부해 백테스트가 전부 실패했다.
     * 구간을 넘겨 닫히는 봉은 평가에서 빼고 체결 봉으로만 보관한다.
     */
    @DisplayName("기간 끝이 봉 경계와 어긋나면 구간을 넘겨 닫히는 봉은 체결 봉으로만 보관한다")
    @Test
    void keepExecutionCandleWhenPeriodIsNotAlignedToCandleBoundary() {
        Instant evaluationStart = Instant.parse("2026-09-16T15:00:00Z");
        Instant misalignedEnd = Instant.parse("2026-09-18T14:59:59.999Z");
        Instant evaluated = Instant.parse("2026-09-17T00:00:00Z");
        Instant closesAfterEnd = Instant.parse("2026-09-18T00:00:00Z");
        Instant openedAfterEnd = Instant.parse("2026-09-19T00:00:00Z");

        BacktestCandleWindow window = new BacktestCandleWindow(
                Timeframe.ONE_DAY,
                evaluationStart,
                misalignedEnd,
                Map.of(ASSET_ID, List.of(
                        dailyCandle(evaluated),
                        dailyCandle(closesAfterEnd),
                        dailyCandle(openedAfterEnd)
                ))
        );

        assertThat(window.candlesByAssetId().get(ASSET_ID))
                .extracting(candle -> candle.getId().getOpenTime())
                .containsExactly(evaluated, closesAfterEnd, openedAfterEnd);
        assertThat(window.evaluationCandlesByAssetId().get(ASSET_ID))
                .extracting(candle -> candle.getId().getOpenTime())
                .containsExactly(evaluated);
    }

    @DisplayName("체결 봉 하나를 넘어서는 봉은 거절한다")
    @Test
    void rejectCandleBeyondExecutionCandle() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new BacktestCandleWindow(
                        Timeframe.ONE_HOUR,
                        EVALUATION_START,
                        EVALUATION_END,
                        Map.of(ASSET_ID, List.of(candle(EVALUATION_END.plusSeconds(3600))))
                ))
                .withMessage("Candle open time must be before one candle after evaluation end");
    }

    private MinuteCandle dailyCandle(Instant openTime) {
        return MinuteCandleFixture.minuteCandle(
                ASSET_ID,
                Timeframe.ONE_DAY.getIntervalMinutes(),
                openTime,
                BigDecimal.ONE,
                BigDecimal.ONE
        );
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
