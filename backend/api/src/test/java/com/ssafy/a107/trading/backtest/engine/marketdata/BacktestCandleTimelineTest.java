package com.ssafy.a107.trading.backtest.engine.marketdata;

import com.ssafy.a107.candle.domain.MinuteCandle;
import com.ssafy.a107.candle.domain.MinuteCandleFixture;
import com.ssafy.a107.strategy.domain.dsl.Timeframe;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BacktestCandleTimelineTest {

    private static final UUID FIRST_ASSET_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID SECOND_ASSET_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final Instant EVALUATION_START = Instant.parse("2026-01-02T00:00:00Z");
    private static final Instant EVALUATION_END = Instant.parse("2026-01-02T03:00:00Z");

    @DisplayName("종목별 평가 봉 시각을 중복 없이 오름차순 타임라인으로 생성한다")
    @Test
    void createTimelineFromEvaluationCandles() {
        BacktestCandleWindow candleWindow = new BacktestCandleWindow(
                Timeframe.ONE_HOUR,
                EVALUATION_START,
                EVALUATION_END,
                Map.of(
                        FIRST_ASSET_ID, List.of(
                                candle(FIRST_ASSET_ID, "2026-01-01T23:00:00Z"),
                                candle(FIRST_ASSET_ID, "2026-01-02T00:00:00Z"),
                                candle(FIRST_ASSET_ID, "2026-01-02T01:00:00Z")
                        ),
                        SECOND_ASSET_ID, List.of(
                                candle(SECOND_ASSET_ID, "2026-01-01T23:00:00Z"),
                                candle(SECOND_ASSET_ID, "2026-01-02T00:00:00Z"),
                                candle(SECOND_ASSET_ID, "2026-01-02T02:00:00Z")
                        )
                )
        );

        BacktestCandleTimeline timeline = BacktestCandleTimeline.from(candleWindow);

        assertThat(timeline.evaluationCandleOpenTimes()).containsExactly(
                Instant.parse("2026-01-02T00:00:00Z"),
                Instant.parse("2026-01-02T01:00:00Z"),
                Instant.parse("2026-01-02T02:00:00Z")
        );
    }

    @DisplayName("타임라인은 입력 순서와 중복 여부와 관계없이 정렬된 불변 목록을 보장한다")
    @Test
    void sortAndDeduplicateOpenTimes() {
        BacktestCandleTimeline timeline = new BacktestCandleTimeline(List.of(
                Instant.parse("2026-01-02T02:00:00Z"),
                Instant.parse("2026-01-02T00:00:00Z"),
                Instant.parse("2026-01-02T02:00:00Z")
        ));

        assertThat(timeline.evaluationCandleOpenTimes()).containsExactly(
                Instant.parse("2026-01-02T00:00:00Z"),
                Instant.parse("2026-01-02T02:00:00Z")
        );
        assertThatThrownBy(() -> timeline.evaluationCandleOpenTimes().add(
                Instant.parse("2026-01-02T03:00:00Z")
        )).isInstanceOf(UnsupportedOperationException.class);
    }

    private MinuteCandle candle(UUID assetId, String openTime) {
        return MinuteCandleFixture.minuteCandle(
                assetId,
                Timeframe.ONE_HOUR.getIntervalMinutes(),
                Instant.parse(openTime),
                BigDecimal.ONE,
                BigDecimal.ONE
        );
    }
}
