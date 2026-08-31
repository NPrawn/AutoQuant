package com.ssafy.a107.auth.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ssafy.a107.auth.config.JwtProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthenticationFilterTest {

    private final JwtTokenProvider tokenProvider = new JwtTokenProvider(
            new JwtProperties("a107-access-token-signing-secret-value", "a107", Duration.ofHours(1))
    );
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
            tokenProvider,
            new AuthenticationErrorWriter(new ObjectMapper().registerModule(new JavaTimeModule()))
    );

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @DisplayName("유효한 토큰을 보내면 인증 주체에 회원 식별자가 담긴다")
    @Test
    void authenticateWithValidToken() throws Exception {
        UUID memberId = UUID.randomUUID();
        MockHttpServletRequest request = requestWithAuthorization(
                "Bearer " + tokenProvider.issue(memberId, Instant.now()).value()
        );
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isEqualTo(new MemberPrincipal(memberId));
        assertThat(filterChain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @DisplayName("httpOnly 쿠키의 토큰으로도 인증된다")
    @Test
    void authenticateWithCookieToken() throws Exception {
        UUID memberId = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/members/me");
        request.setCookies(new jakarta.servlet.http.Cookie(
                AccessTokenCookie.NAME,
                tokenProvider.issue(memberId, Instant.now()).value()
        ));
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), filterChain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isEqualTo(new MemberPrincipal(memberId));
        assertThat(filterChain.getRequest()).isNotNull();
    }

    @DisplayName("토큰이 없으면 인증하지 않고 다음 필터로 넘긴다")
    @Test
    void passThroughWithoutToken() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(new MockHttpServletRequest(), response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(filterChain.getRequest()).isNotNull();
    }

    @DisplayName("Bearer 형식이 아닌 헤더는 토큰이 없는 것으로 다룬다")
    @Test
    void ignoreNonBearerHeader() throws Exception {
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(requestWithAuthorization("Basic dXNlcjpwYXNz"), new MockHttpServletResponse(), filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(filterChain.getRequest()).isNotNull();
    }

    @DisplayName("위조된 토큰을 보내면 다음 필터로 넘기지 않고 인증 오류를 응답한다")
    @Test
    void rejectInvalidToken() throws Exception {
        MockHttpServletRequest request = requestWithAuthorization("Bearer forged-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("AUTH-401-003");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(filterChain.getRequest()).isNull();
    }

    private static MockHttpServletRequest requestWithAuthorization(String headerValue) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/members/me");
        request.addHeader("Authorization", headerValue);
        return request;
    }
}
