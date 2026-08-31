package com.ssafy.a107.auth.controller.request;

import jakarta.validation.constraints.NotBlank;

/**
 * SSAFY 인증 서버가 redirect_uri 로 전달한 인가 코드.
 */
public record SsafyLoginRequest(
        @NotBlank String authorizationCode
) {
}
