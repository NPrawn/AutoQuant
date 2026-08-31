package com.ssafy.a107.auth.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/* 서비스가 발급하는 액세스 토큰 설정. secret 은 HS256 최소 길이인 32바이트 이상이어야 한다. */
@Validated
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        @NotBlank @Size(min = 32) String secret,
        @NotBlank String issuer,
        @NotNull Duration accessTokenValidity
) {
}
