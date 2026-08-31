package com.ssafy.a107.auth.infrastructure.ssafy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/* SSAFY 토큰 응답. 필드명은 명세를 그대로 따르고, MVP 에서 안 쓰는 refresh token 은 받지 않는다. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SsafyTokenResponse(
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("expires_in") String expiresIn,
        @JsonProperty("scope") String scope
) {
}
