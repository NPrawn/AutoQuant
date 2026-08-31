package com.ssafy.a107.auth.service.port;

/* SSAFY 인증 서버와 통신하는 유일한 통로. 구현체만 HTTP 를 안다. */
public interface SsafyAuthClient {

    /* 인가 코드를 액세스 토큰으로 교환한다. 사용자 정보를 한 번만 읽으므로 refresh token 은 보관하지 않는다. */
    String exchangeAuthorizationCode(String authorizationCode);

    /* 액세스 토큰으로 로그인 처리에 필요한 사용자 정보를 조회한다. */
    SsafyUserInfo fetchUserInfo(String ssafyAccessToken);
}
