package com.ssafy.a107.candle.controller.response;

import com.ssafy.a107.candle.domain.MinuteCandleCoverage;

import java.time.Instant;

/* 봉이 없으면 start 와 end 가 null 이다. 조회 실패가 아니라 아직 쌓인 봉이 없다는 뜻이다. */
public record CandleCoverageResponse(int intervalMinutes, Instant start, Instant end) {

    public static CandleCoverageResponse from(int intervalMinutes, MinuteCandleCoverage coverage) {
        if (coverage.isEmpty()) {
            return new CandleCoverageResponse(intervalMinutes, null, null);
        }
        return new CandleCoverageResponse(intervalMinutes, coverage.oldestOpenTime(), coverage.newestOpenTime());
    }
}
