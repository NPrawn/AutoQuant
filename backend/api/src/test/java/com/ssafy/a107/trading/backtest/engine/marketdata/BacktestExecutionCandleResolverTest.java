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

class BacktestExecutionCandleResolverTest {

    private static final UUID FIRST_ASSET_ID = UUID.fromString(
            "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
    );
    private static final UUID SECOND_ASSET_ID = UUID.fromString(
            "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"
    );
    private static final Instant EVALUATION_START = Instant.parse(
            "2026-01-02T00:00:00Z"
    );
    private static final Instant EVALUATION_END = Instant.parse(
            "2026-01-02T02:00:00Z"
    );

    private final BacktestExecutionCandleResolver resolver =
            new BacktestExecutionCandleResolver();

    @DisplayName("신호 봉 다음 시각의 봉을 체결 봉으로 조회한다")
    @Test
    void findNextExecutionCandle() {
        BacktestCandleWindow candleWindow = candleWindow();

        MinuteCandle executionCandle = resolver.findNextExecutionCandle(
                candleWindow,
                FIRST_ASSET_ID,
                EVALUATION_START
        ).orElseThrow();

        assertThat(executionCandle.getId().getOpenTime())
                .isEqualTo(Instant.parse("2026-01-02T01:00:00Z"));
        assertThat(executionCandle.getOpenPrice())
                .isEqualByComparingTo("110");
    }

    @DisplayName("신호 시각에 봉이 있는 종목의 종가만 현재가로 반환한다")
    @Test
    void resolveClosePricesAtSignalCandle() {
        Map<UUID, BigDecimal> closePrices = resolver.resolveClosePrices(
                candleWindow(),
                EVALUATION_START
        );

        assertThat(closePrices).containsEntry(
                FIRST_ASSET_ID,
                BigDecimal.valueOf(100)
        );
        assertThat(closePrices).doesNotContainKey(SECOND_ASSET_ID);
    }

    @DisplayName("다음 체결 봉이 없으면 빈 결과를 반환한다")
    @Test
    void returnEmptyWhenNextExecutionCandleIsMissing() {
        assertThat(resolver.findNextExecutionCandle(
                candleWindow(),
                FIRST_ASSET_ID,
                Instant.parse("2026-01-02T02:00:00Z")
        )).isEmpty();
    }

    private BacktestCandleWindow candleWindow() {
        return new BacktestCandleWindow(
                Timeframe.ONE_HOUR,
                EVALUATION_START,
                EVALUATION_END,
                Map.of(
                        FIRST_ASSET_ID,
                        List.of(
                                candle(
                                        FIRST_ASSET_ID,
                                        EVALUATION_START,
                                        BigDecimal.valueOf(100)
                                ),
                                candle(
                                        FIRST_ASSET_ID,
                                        Instant.parse("2026-01-02T01:00:00Z"),
                                        BigDecimal.valueOf(110)
                                ),
                                candle(
                                        FIRST_ASSET_ID,
                                        EVALUATION_END,
                                        BigDecimal.valueOf(120)
                                )
                        ),
                        SECOND_ASSET_ID,
                        List.of(candle(
                                SECOND_ASSET_ID,
                                Instant.parse("2026-01-02T01:00:00Z"),
                                BigDecimal.valueOf(200)
                        ))
                )
        );
    }

    private MinuteCandle candle(
            UUID assetId,
            Instant openTime,
            BigDecimal price
    ) {
        return MinuteCandleFixture.minuteCandle(
                assetId,
                Timeframe.ONE_HOUR.getIntervalMinutes(),
                openTime,
                price,
                BigDecimal.ONE
        );
    }
}
