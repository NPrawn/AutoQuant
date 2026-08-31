package com.ssafy.a107.auth.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/* SSAFY 인증 서버 연동 설정. client-id 와 client-secret 은 기본값이 없어 비어 있으면 기동되지 않는다. */
@Validated
@ConfigurationProperties(prefix = "ssafy.oauth2")
public record SsafyOAuth2Properties(
        @NotBlank
        String baseUrl,

        @NotBlank
        String clientId,

        @NotBlank
        String clientSecret,

        @NotBlank
        String redirectUri,

        @NotBlank
        String authorizePath,

        @NotBlank
        String tokenPath,

        @NotBlank
        String userInfoPath,

        @NotNull
        Duration timeout
) {

    @AssertTrue(message = "SSAFY timeout은 0보다 커야 합니다.")
    public boolean isTimeoutPositive() {
        return timeout == null || (!timeout.isZero() && !timeout.isNegative());
    }
}
