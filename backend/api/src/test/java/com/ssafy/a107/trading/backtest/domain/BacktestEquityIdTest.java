package com.ssafy.a107.trading.backtest.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BacktestEquityIdTest {

    private static final UUID BACKTEST_ID = UUID.fromString(
            "e4b4e9e0-8d17-4a47-98d4-4d9f06d97b2b"
    );
    private static final Instant VALUATION_AT = Instant.parse("2026-01-02T00:00:00Z");

    @DisplayName("백테스트 ID와 평가 시각이 같으면 동일한 복합키다")
    @Test
    void equalWhenBacktestIdAndValuationAtAreSame() {
        BacktestEquityId first = new BacktestEquityId(BACKTEST_ID, VALUATION_AT);
        BacktestEquityId second = new BacktestEquityId(BACKTEST_ID, VALUATION_AT);

        assertThat(first)
                .isEqualTo(second)
                .hasSameHashCodeAs(second);
    }

    @DisplayName("평가 시각이 다르면 서로 다른 복합키다")
    @Test
    void notEqualWhenValuationAtIsDifferent() {
        BacktestEquityId first = new BacktestEquityId(BACKTEST_ID, VALUATION_AT);
        BacktestEquityId second = new BacktestEquityId(
                BACKTEST_ID,
                VALUATION_AT.plusSeconds(3_600)
        );

        assertThat(first).isNotEqualTo(second);
    }
}
