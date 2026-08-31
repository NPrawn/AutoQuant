package com.ssafy.a107.auth.jwt;

import com.ssafy.a107.auth.config.JwtProperties;
import com.ssafy.a107.common.exception.ApiErrorCode;
import com.ssafy.a107.common.exception.ApiException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    private final SecretKey signingKey;
    private final JwtProperties properties;

    public JwtTokenProvider(JwtProperties properties) {
        this.properties = properties;
        this.signingKey = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public IssuedAccessToken issue(UUID memberId, Instant issuedAt) {
        Instant expiresAt = issuedAt.plus(properties.accessTokenValidity());
        String value = Jwts.builder()
                .issuer(properties.issuer())
                .subject(memberId.toString())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();

        return new IssuedAccessToken(value, expiresAt);
    }

    /* 서명과 만료를 검증하고 회원 식별자를 꺼낸다. 만료와 위조를 구분해서 알려주지 않는다. */
    public UUID parseMemberId(String accessToken) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(properties.issuer())
                    .build()
                    .parseSignedClaims(accessToken)
                    .getPayload();

            return UUID.fromString(claims.getSubject());
        } catch (JwtException | IllegalArgumentException exception) {
            throw new ApiException(ApiErrorCode.AUTH_INVALID_CREDENTIAL, exception);
        }
    }
}
