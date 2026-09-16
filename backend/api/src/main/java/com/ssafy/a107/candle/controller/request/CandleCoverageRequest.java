package com.ssafy.a107.candle.controller.request;

/* 간격을 주지 않으면 일봉으로 본다. 백테스트 기간 경계가 가장 자주 쓰는 값이다. */
public record CandleCoverageRequest(Integer intervalMinutes) {

    private static final int DEFAULT_INTERVAL_MINUTES = 1440;

    public CandleCoverageRequest {
        if (intervalMinutes == null) {
            intervalMinutes = DEFAULT_INTERVAL_MINUTES;
        }
    }
}
