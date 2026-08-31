package com.ssafy.a107.asset.infrastructure.upbit;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
record UpbitMarketResponse(
        @JsonProperty("market") String exchangeMarketCode,
        @JsonProperty("korean_name") String koreanName,
        @JsonProperty("english_name") String englishName
) {
}
