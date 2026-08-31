package com.ssafy.a107.auth.service;

import com.ssafy.a107.auth.client.SsafyAuthClient;
import com.ssafy.a107.auth.client.SsafyUserInfo;
import com.ssafy.a107.auth.config.SsafyOAuth2Properties;
import com.ssafy.a107.auth.controller.response.LoginResponse;
import com.ssafy.a107.auth.jwt.IssuedAccessToken;
import com.ssafy.a107.auth.jwt.JwtTokenProvider;
import com.ssafy.a107.common.exception.ApiErrorCode;
import com.ssafy.a107.common.exception.ApiException;
import com.ssafy.a107.member.domain.LoginProvider;
import com.ssafy.a107.member.domain.Member;
import com.ssafy.a107.member.service.MemberCommandService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class AuthCommandServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-31T05:00:00Z");
    private static final UUID MEMBER_ID = UUID.fromString("0199aa2f-1f4d-4c1b-9c58-0d1f7f6a1c11");

    @Mock
    private SsafyAuthClient ssafyAuthClient;

    @Mock
    private MemberCommandService memberCommandService;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    private AuthCommandService authCommandService;

    @BeforeEach
    void setUp() {
        authCommandService = new AuthCommandService(
                ssafyAuthClient,
                properties(),
                memberCommandService,
                jwtTokenProvider,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @DisplayName("인가 URL에 client_id와 redirect_uri와 response_type을 붙인다")
    @Test
    void createAuthorizeUrl() {
        String authorizeUrl = authCommandService.createAuthorizeUrl().authorizeUrl();

        assertThat(authorizeUrl)
                .startsWith("https://project.ssafy.com/oauth/sso-check?")
                .contains("client_id=client-id")
                .contains("response_type=code")
                .contains("redirect_uri=http%3A%2F%2Flocalhost%3A5173%2Fauth%2Fssafy%2Fcallback");
    }

    @DisplayName("인가 코드로 로그인하면 서비스 액세스 토큰과 회원 정보를 반환한다")
    @Test
    void login() {
        given(ssafyAuthClient.exchangeAuthorizationCode("authorization-code")).willReturn("ssafy-access-token");
        given(ssafyAuthClient.fetchUserInfo("ssafy-access-token"))
                .willReturn(new SsafyUserInfo("1234567", "gaon@ssafy.com", "가온"));
        given(memberCommandService.registerOrRecordLogin(
                LoginProvider.SSAFY, "1234567", "gaon@ssafy.com", "가온", NOW
        )).willReturn(member());
        given(jwtTokenProvider.issue(MEMBER_ID, NOW))
                .willReturn(new IssuedAccessToken("service-access-token", NOW.plus(Duration.ofHours(1))));

        LoginResponse response = authCommandService.login("authorization-code");

        assertThat(response.accessToken()).isEqualTo("service-access-token");
        assertThat(response.accessTokenExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(1)));
        assertThat(response.memberId()).isEqualTo(MEMBER_ID);
        assertThat(response.displayName()).isEqualTo("가온");
        assertThat(response.emailAddress()).isEqualTo("gaon@ssafy.com");
    }

    @DisplayName("인가 코드가 비어 있으면 SSAFY를 호출하지 않고 거부한다")
    @Test
    void rejectBlankAuthorizationCode() {
        assertThatThrownBy(() -> authCommandService.login("  "))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ApiErrorCode.AUTH_INVALID_AUTHORIZATION_CODE));

        then(ssafyAuthClient).should(never()).exchangeAuthorizationCode(any());
    }

    @DisplayName("SSAFY 사용자 정보 조회가 실패하면 회원을 만들지 않는다")
    @Test
    void doNotRegisterWhenUserInfoFails() {
        given(ssafyAuthClient.exchangeAuthorizationCode("authorization-code")).willReturn("ssafy-access-token");
        given(ssafyAuthClient.fetchUserInfo("ssafy-access-token"))
                .willThrow(new ApiException(ApiErrorCode.AUTH_CONSENT_REQUIRED));

        assertThatThrownBy(() -> authCommandService.login("authorization-code"))
                .isInstanceOf(ApiException.class);

        then(memberCommandService).should(never())
                .registerOrRecordLogin(any(), any(), any(), any(), any());
        then(jwtTokenProvider).should(never()).issue(any(), any());
    }

    @DisplayName("정지된 회원이면 토큰을 발급하지 않는다")
    @Test
    void doNotIssueTokenForInactiveMember() {
        given(ssafyAuthClient.exchangeAuthorizationCode("authorization-code")).willReturn("ssafy-access-token");
        given(ssafyAuthClient.fetchUserInfo("ssafy-access-token"))
                .willReturn(new SsafyUserInfo("1234567", "gaon@ssafy.com", "가온"));
        given(memberCommandService.registerOrRecordLogin(any(), any(), any(), any(), eq(NOW)))
                .willThrow(new ApiException(ApiErrorCode.AUTH_MEMBER_NOT_ACTIVE));

        assertThatThrownBy(() -> authCommandService.login("authorization-code"))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ApiErrorCode.AUTH_MEMBER_NOT_ACTIVE));

        then(jwtTokenProvider).should(never()).issue(any(), any());
    }

    /* memberId 는 영속화 시점에 생성되므로 단위 테스트에서는 리플렉션으로 채운다. */
    private static Member member() {
        Member member = new Member(LoginProvider.SSAFY, "1234567", "gaon@ssafy.com", "가온");
        ReflectionTestUtils.setField(member, "memberId", MEMBER_ID);
        return member;
    }

    private static SsafyOAuth2Properties properties() {
        return new SsafyOAuth2Properties(
                "client-id",
                "client-secret",
                "http://localhost:5173/auth/ssafy/callback",
                "https://project.ssafy.com/oauth/sso-check",
                "https://project.ssafy.com/ssafy/oauth2/token",
                "https://project.ssafy.com/ssafy/resources/userInfo",
                Duration.ofSeconds(3),
                Duration.ofSeconds(5)
        );
    }
}
