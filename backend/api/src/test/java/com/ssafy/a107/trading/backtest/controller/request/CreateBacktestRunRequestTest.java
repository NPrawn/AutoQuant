package com.ssafy.a107.trading.backtest.controller.request;

import com.ssafy.a107.trading.backtest.domain.ExecutionPriceRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class CreateBacktestRunRequestTest {

    @DisplayName("백테스트 요청 DTO는 서비스 명령으로 변환된다")
    @Test
    void convertToCommand() {
        CreateBacktestRunRequest request = new CreateBacktestRunRequest(
                1L,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-02-01T00:00:00Z"),
                "KRW",
                BigDecimal.valueOf(1_000_000),
                BigDecimal.valueOf(0.0005),
                BigDecimal.valueOf(5),
                ExecutionPriceRule.NEXT_CANDLE_OPEN,
                "engine-v1",
                "market-v1"
        );

        assertThat(request.toCommand().strategyId()).isEqualTo(1L);
        assertThat(request.toCommand().periodStart()).isEqualTo(request.periodStart());
        assertThat(request.toCommand().periodEnd()).isEqualTo(request.periodEnd());
        assertThat(request.toCommand().quoteCurrency()).isEqualTo("KRW");
        assertThat(request.toCommand().executionPriceRule()).isEqualTo(ExecutionPriceRule.NEXT_CANDLE_OPEN);
    }
}
