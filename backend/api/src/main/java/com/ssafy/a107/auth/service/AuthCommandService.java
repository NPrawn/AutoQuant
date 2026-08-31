package com.ssafy.a107.auth.service;

import com.ssafy.a107.auth.config.SsafyOAuth2Properties;
import com.ssafy.a107.auth.service.port.SsafyAuthClient;
import com.ssafy.a107.auth.service.port.SsafyUserInfo;
import com.ssafy.a107.auth.controller.response.LoginResponse;
import com.ssafy.a107.auth.controller.response.SsafyAuthorizeUrlResponse;
import com.ssafy.a107.auth.jwt.IssuedAccessToken;
import com.ssafy.a107.auth.jwt.JwtTokenProvider;
import com.ssafy.a107.common.exception.ApiErrorCode;
import com.ssafy.a107.common.exception.ApiException;
import com.ssafy.a107.member.domain.LoginProvider;
import com.ssafy.a107.member.domain.Member;
import com.ssafy.a107.member.service.MemberCommandService;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;

/* SSAFY 로그인 절차를 조립한다. 외부 HTTP 를 DB 트랜잭션 안에서 부르지 않으려고 여기서는 트랜잭션을 열지 않는다. */
@Service
public class AuthCommandService {

    private static final String RESPONSE_TYPE_CODE = "code";

    private final SsafyAuthClient ssafyAuthClient;
    private final SsafyOAuth2Properties properties;
    private final MemberCommandService memberCommandService;
    private final JwtTokenProvider jwtTokenProvider;
    private final Clock clock;

    public AuthCommandService(
            SsafyAuthClient ssafyAuthClient,
            SsafyOAuth2Properties properties,
            MemberCommandService memberCommandService,
            JwtTokenProvider jwtTokenProvider,
            Clock clock
    ) {
        this.ssafyAuthClient = ssafyAuthClient;
        this.properties = properties;
        this.memberCommandService = memberCommandService;
        this.jwtTokenProvider = jwtTokenProvider;
        this.clock = clock;
    }

    public SsafyAuthorizeUrlResponse createAuthorizeUrl() {
        // redirect_uri 의 콜론과 슬래시는 쿼리에서 합법이라 기본 인코딩으로는 남는다. 값을 직접 인코딩해서 넘긴다.
        String authorizeUrl = UriComponentsBuilder.fromUriString(properties.baseUrl())
                .path(properties.authorizePath())
                .queryParam("client_id", encode(properties.clientId()))
                .queryParam("redirect_uri", encode(properties.redirectUri()))
                .queryParam("response_type", RESPONSE_TYPE_CODE)
                .build(true)
                .toUriString();

        return new SsafyAuthorizeUrlResponse(authorizeUrl);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public LoginResponse login(String authorizationCode) {
        if (authorizationCode == null || authorizationCode.isBlank()) {
            throw new ApiException(ApiErrorCode.AUTH_INVALID_AUTHORIZATION_CODE);
        }

        String ssafyAccessToken = ssafyAuthClient.exchangeAuthorizationCode(authorizationCode);
        SsafyUserInfo userInfo = ssafyAuthClient.fetchUserInfo(ssafyAccessToken);

        Instant now = clock.instant();
        Member member = memberCommandService.registerOrRecordLogin(
                LoginProvider.SSAFY,
                userInfo.userId(),
                userInfo.email(),
                userInfo.name(),
                now
        );

        IssuedAccessToken accessToken = jwtTokenProvider.issue(member.getMemberId(), now);

        return new LoginResponse(
                accessToken.value(),
                accessToken.expiresAt(),
                member.getMemberId(),
                member.getDisplayName(),
                member.getEmailAddress()
        );
    }
}
