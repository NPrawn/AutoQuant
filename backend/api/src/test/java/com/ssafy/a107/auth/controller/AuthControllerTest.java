package com.ssafy.a107.auth.controller;

import com.ssafy.a107.auth.config.AuthLoginProperties;
import com.ssafy.a107.auth.config.JwtProperties;
import com.ssafy.a107.auth.jwt.IssuedAccessToken;
import com.ssafy.a107.auth.service.AuthCommandService;
import com.ssafy.a107.common.exception.ApiErrorCode;
import com.ssafy.a107.common.exception.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private static final String FRONTEND = "http://localhost:5173";

    @Mock
    private AuthCommandService authCommandService;

    @DisplayName("로그인에 성공하면 토큰을 httpOnly 쿠키로 심고 프론트로 리다이렉트한다")
    @Test
    void setCookieAndRedirectOnSuccess() {
        given(authCommandService.login("authorization-code"))
                .willReturn(new IssuedAccessToken("service-access-token", Instant.parse("2026-08-31T06:00:00Z")));

        ResponseEntity<Void> response = controller().completeLogin("authorization-code");

        assertThat(response.getStatusCode().value()).isEqualTo(302);
        assertThat(response.getHeaders().getLocation()).hasToString(FRONTEND);
        String setCookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(setCookie)
                .contains("access_token=service-access-token")
                .contains("HttpOnly")
                .contains("SameSite=Lax")
                .contains("Path=/");
    }

    @DisplayName("로그인에 실패하면 쿠키 없이 오류 코드를 붙여 프론트로 리다이렉트한다")
    @Test
    void redirectWithErrorCodeOnFailure() {
        given(authCommandService.login("expired-code"))
                .willThrow(new ApiException(ApiErrorCode.AUTH_AUTHORIZATION_CODE_REJECTED));

        ResponseEntity<Void> response = controller().completeLogin("expired-code");

        assertThat(response.getStatusCode().value()).isEqualTo(302);
        assertThat(response.getHeaders().getLocation())
                .hasToString(FRONTEND + "/login?error=AUTH-401-001");
        assertThat(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).isNull();
    }

    private AuthController controller() {
        return new AuthController(
                authCommandService,
                new AuthLoginProperties(FRONTEND, false),
                new JwtProperties("a107-access-token-signing-secret-value", "a107", Duration.ofHours(1))
        );
    }
}
