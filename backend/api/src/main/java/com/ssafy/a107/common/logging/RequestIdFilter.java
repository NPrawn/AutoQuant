package com.ssafy.a107.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 요청 하나를 Spring 로그와 AI 서버 로그 양쪽에서 같은 값으로 찾게 한다.
 *
 * <p>규칙은 세 서버가 같다 — 헤더가 있으면 이어받고 없으면 만든다. 각 홉이 같은 규칙을 쓰면
 * 어느 지점에서 추적을 시작하든 그 아래가 이어진다. 인증 실패 로그에도 값이 있어야 해서
 * 이 필터가 {@code JwtAuthenticationFilter} 를 포함한 보안 체인보다 앞에 선다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Request-ID";
    public static final String MDC_KEY = "requestId";

    /** 현재 요청의 식별자. 요청 스레드 밖에서는 비어 있다. */
    public static String current() {
        return MDC.get(MDC_KEY);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestId = resolveRequestId(request);
        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER_NAME, requestId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            // 톰캣이 스레드를 재사용하므로 지우지 않으면 다음 요청 로그에 이전 값이 찍힌다.
            MDC.remove(MDC_KEY);
        }
    }

    private String resolveRequestId(HttpServletRequest request) {
        String incoming = request.getHeader(HEADER_NAME);
        return (incoming == null || incoming.isBlank()) ? UUID.randomUUID().toString() : incoming;
    }
}
