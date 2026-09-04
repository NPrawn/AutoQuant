package com.ssafy.a107.trading.backtest.controller.response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class BacktestRunResponseTest {

    @DisplayName("백테스트 실행 응답은 내부 실패 원인을 포함하지 않는다")
    @Test
    void doesNotExposeFailureReason() {
        assertThat(Arrays.stream(BacktestRunResponse.class.getRecordComponents())
                .map(component -> component.getName()))
                .doesNotContain("failureReason");
    }
}
