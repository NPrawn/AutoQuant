package com.ssafy.a107.auth.controller.response;

import java.time.Instant;
import java.util.UUID;

/**
 * 로그인 결과. SSAFY 액세스 토큰은 담지 않는다.
 */
public record LoginResponse(
        String accessToken,
        Instant accessTokenExpiresAt,
        UUID memberId,
        String displayName,
        String emailAddress
) {
}
