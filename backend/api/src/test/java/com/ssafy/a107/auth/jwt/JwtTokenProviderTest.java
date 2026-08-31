package com.ssafy.a107.auth.jwt;

import com.ssafy.a107.auth.config.JwtProperties;
import com.ssafy.a107.common.exception.ApiErrorCode;
import com.ssafy.a107.common.exception.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    private static final String SECRET = "a107-access-token-signing-secret-value";
    private static final String ISSUER = "a107";

    private final JwtTokenProvider tokenProvider = new JwtTokenProvider(
            new JwtProperties(SECRET, ISSUER, Duration.ofHours(1))
    );

    @DisplayName("발급한 토큰에서 회원 식별자를 꺼낸다")
    @Test
    void parseMemberIdFromIssuedToken() {
        UUID memberId = UUID.randomUUID();

        // 만료 검증은 실제 시계를 쓰므로 발급 시각을 고정하지 않는다.
        IssuedAccessToken accessToken = tokenProvider.issue(memberId, Instant.now());

        assertThat(tokenProvider.parseMemberId(accessToken.value())).isEqualTo(memberId);
    }

    @DisplayName("액세스 토큰 만료 시각은 발급 시각에 유효 기간을 더한 값이다")
    @Test
    void expiresAtIsIssuedAtPlusValidity() {
        Instant issuedAt = Instant.parse("2026-08-31T00:00:00Z");

        IssuedAccessToken accessToken = tokenProvider.issue(UUID.randomUUID(), issuedAt);

        assertThat(accessToken.expiresAt()).isEqualTo(issuedAt.plus(Duration.ofHours(1)));
    }

    @DisplayName("만료된 토큰은 인증 정보가 유효하지 않다고 거부한다")
    @Test
    void rejectExpiredToken() {
        JwtTokenProvider shortLivedProvider = new JwtTokenProvider(
                new JwtProperties(SECRET, ISSUER, Duration.ofSeconds(1))
        );
        String expired = shortLivedProvider
                .issue(UUID.randomUUID(), Instant.now().minus(Duration.ofHours(1)))
                .value();

        assertThatThrownBy(() -> shortLivedProvider.parseMemberId(expired))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ApiErrorCode.AUTH_INVALID_CREDENTIAL));
    }

    @DisplayName("다른 키로 서명한 토큰은 거부한다")
    @Test
    void rejectTokenSignedWithAnotherKey() {
        String forged = new JwtTokenProvider(
                new JwtProperties("another-signing-secret-value-for-test", ISSUER, Duration.ofHours(1))
        ).issue(UUID.randomUUID(), Instant.now()).value();

        assertThatThrownBy(() -> tokenProvider.parseMemberId(forged))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ApiErrorCode.AUTH_INVALID_CREDENTIAL));
    }

    @DisplayName("토큰 형식이 아닌 문자열은 거부한다")
    @Test
    void rejectMalformedToken() {
        assertThatThrownBy(() -> tokenProvider.parseMemberId("not-a-token"))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ApiErrorCode.AUTH_INVALID_CREDENTIAL));
    }
}
