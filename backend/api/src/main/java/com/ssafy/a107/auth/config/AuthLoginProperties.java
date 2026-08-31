package com.ssafy.a107.auth.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/* 로그인 완료 후 브라우저를 돌려보낼 프론트 주소와 토큰 쿠키의 secure 여부. */
@Validated
@ConfigurationProperties(prefix = "auth")
public record AuthLoginProperties(
        @NotBlank
        String frontendBaseUrl,

        boolean cookieSecure
) {
}
