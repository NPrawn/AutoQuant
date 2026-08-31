package com.ssafy.a107.auth.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/* SSAFY 인증 서버 연동 설정. client-id 와 client-secret 은 기본값이 없어 비어 있으면 기동되지 않는다. */
@Validated
@ConfigurationProperties(prefix = "ssafy.oauth2")
public record SsafyOAuth2Properties(
        @NotBlank String clientId,
        @NotBlank String clientSecret,
        @NotBlank String redirectUri,
        @NotBlank String authorizeUrl,
        @NotBlank String tokenUrl,
        @NotBlank String userInfoUrl,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout
) {
}
