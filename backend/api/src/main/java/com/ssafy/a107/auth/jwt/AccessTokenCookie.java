package com.ssafy.a107.auth.jwt;

import org.springframework.http.ResponseCookie;

import java.time.Duration;

/*
 * 토큰 쿠키. 로그인 콜백이 심고 로그아웃이 지우며 인증 필터가 읽는다. 심을 때와 지울 때의 속성이
 * 하나라도 다르면 브라우저가 다른 쿠키로 보고 지우지 못하므로 생성 책임을 이 클래스에만 둔다.
 */
public final class AccessTokenCookie {

    public static final String NAME = "access_token";

    private static final String PATH = "/";
    private static final String SAME_SITE = "Lax";

    private AccessTokenCookie() {
    }

    /* 로그인 성공 시 심는 쿠키. httpOnly 라 브라우저 JS 가 토큰을 읽을 수 없다. */
    public static ResponseCookie issued(String token, boolean secure, Duration validity) {
        return builder(token, secure)
                .maxAge(validity)
                .build();
    }

    /* 로그아웃 시 브라우저가 쿠키를 버리도록 즉시 만료시킨다. httpOnly 라 프론트가 직접 지울 수 없다. */
    public static ResponseCookie expired(boolean secure) {
        return builder("", secure)
                .maxAge(Duration.ZERO)
                .build();
    }

    private static ResponseCookie.ResponseCookieBuilder builder(String value, boolean secure) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite(SAME_SITE)
                .path(PATH);
    }
}
