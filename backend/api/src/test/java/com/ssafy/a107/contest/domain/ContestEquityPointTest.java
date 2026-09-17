package com.ssafy.a107.contest.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ContestEquityPointTest {

    @DisplayName("하루에 그날 닫힌 마지막 기록 하나만 남긴다")
    @Test
    void keepTheLastCloseOfEachDay() {
        List<ContestEquityPoint> hourly = List.of(
                point("2030-08-05T01:00:00Z", "0.01"),
                point("2030-08-05T23:00:00Z", "0.02"),
                /* 00:00 기록은 전날 23시 봉이 닫힌 값이다. */
                point("2030-08-06T00:00:00Z", "0.03"),
                point("2030-08-06T01:00:00Z", "0.04"),
                point("2030-08-07T00:00:00Z", "-0.05")
        );

        assertThat(ContestEquityPoint.lastOfEachDay(hourly)).containsExactly(
                point("2030-08-06T00:00:00Z", "0.03"),
                point("2030-08-07T00:00:00Z", "-0.05")
        );
    }

    @DisplayName("일봉은 이미 하루 한 점이라 그대로다")
    @Test
    void keepDailyPointsAsTheyAre() {
        List<ContestEquityPoint> daily = List.of(
                point("2030-08-06T00:00:00Z", "0.01"),
                point("2030-08-07T00:00:00Z", "0.02")
        );

        assertThat(ContestEquityPoint.lastOfEachDay(daily)).isEqualTo(daily);
    }

    private static ContestEquityPoint point(String valuationAt, String returnRate) {
        return new ContestEquityPoint(Instant.parse(valuationAt), new BigDecimal(returnRate));
    }
}
