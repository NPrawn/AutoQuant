package com.ssafy.a107.asset.infrastructure.upbit;

import com.fasterxml.jackson.annotation.JsonProperty;

/* 상장일을 찾는 데는 봉의 시각만 쓴다. 가격과 거래량은 candle 패키지가 따로 읽는다. */
public record UpbitDailyCandleResponse(
        @JsonProperty("candle_date_time_utc")
        String candleDateTimeUtc
) {
}
