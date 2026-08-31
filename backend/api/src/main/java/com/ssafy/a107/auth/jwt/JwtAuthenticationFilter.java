package com.ssafy.a107.auth.jwt;

import com.ssafy.a107.common.exception.ApiException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationErrorWriter errorWriter;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider, AuthenticationErrorWriter errorWriter) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.errorWriter = errorWriter;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String accessToken = resolveAccessToken(request);

        // 토큰이 없으면 통과시킨다. 인증이 필요한지는 인가 규칙이 판단한다.
        if (accessToken == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            UUID memberId = jwtTokenProvider.parseMemberId(accessToken);
            MemberPrincipal principal = new MemberPrincipal(memberId);
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(principal, null, List.of())
            );
        } catch (ApiException exception) {
            // 토큰을 보냈는데 유효하지 않은 경우는 인증 없이 보낸 경우와 구분해서 응답한다.
            SecurityContextHolder.clearContext();
            errorWriter.write(request, response, exception.getErrorCode());
            return;
        }

        filterChain.doFilter(request, response);
    }

    /* 브라우저는 httpOnly 쿠키로, 그 외 클라이언트는 Authorization 헤더로 토큰을 보낸다. 헤더를 우선한다. */
    private static String resolveAccessToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String value = header.substring(BEARER_PREFIX.length()).trim();
            if (!value.isEmpty()) {
                return value;
            }
        }
        return resolveCookieToken(request);
    }

    private static String resolveCookieToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (AccessTokenCookie.NAME.equals(cookie.getName())) {
                String value = cookie.getValue();
                return (value == null || value.isEmpty()) ? null : value;
            }
        }
        return null;
    }
}
