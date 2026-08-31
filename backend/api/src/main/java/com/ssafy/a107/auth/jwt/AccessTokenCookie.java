package com.ssafy.a107.auth.jwt;

/* 토큰 쿠키 이름. 콜백이 쓰고 인증 필터가 읽으므로 한 곳에만 둔다. */
public final class AccessTokenCookie {

    public static final String NAME = "access_token";

    private AccessTokenCookie() {
    }
}
