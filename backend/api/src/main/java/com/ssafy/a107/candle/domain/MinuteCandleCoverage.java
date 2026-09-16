package com.ssafy.a107.candle.domain;

import java.time.Instant;

/*
 * 특정 간격으로 보유한 봉의 처음과 마지막 시각이다. 봉이 하나도 없으면 두 값 모두 비어 있다.
 * 여러 종목을 합친 값이라 상장이 이른 종목이 처음을, 수집이 살아 있는 종목이 마지막을 정한다.
 */
public record MinuteCandleCoverage(Instant oldestOpenTime, Instant newestOpenTime) {

    public static MinuteCandleCoverage empty() {
        return new MinuteCandleCoverage(null, null);
    }

    public boolean isEmpty() {
        return oldestOpenTime == null || newestOpenTime == null;
    }
}
