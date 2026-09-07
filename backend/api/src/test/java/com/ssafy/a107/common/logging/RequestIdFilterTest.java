package com.ssafy.a107.common.logging;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @DisplayName("헤더가 없으면 요청 식별자를 만들어 응답 헤더로 돌려준다")
    @Test
    void createRequestIdWhenHeaderIsMissing() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> seenInsideChain = new AtomicReference<>();

        filter.doFilter(new MockHttpServletRequest(), response, captureRequestId(seenInsideChain));

        assertThat(seenInsideChain.get()).isNotBlank();
        assertThat(response.getHeader(RequestIdFilter.HEADER_NAME)).isEqualTo(seenInsideChain.get());
        assertThat(UUID.fromString(seenInsideChain.get())).isNotNull();
    }

    @DisplayName("헤더를 달고 들어온 요청은 그 값을 그대로 쓴다")
    @Test
    void reuseIncomingRequestId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.HEADER_NAME, "req-from-frontend");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> seenInsideChain = new AtomicReference<>();

        filter.doFilter(request, response, captureRequestId(seenInsideChain));

        assertThat(seenInsideChain.get()).isEqualTo("req-from-frontend");
        assertThat(response.getHeader(RequestIdFilter.HEADER_NAME)).isEqualTo("req-from-frontend");
    }

    @DisplayName("빈 헤더는 값이 없는 것으로 보고 새로 만든다")
    @Test
    void treatBlankHeaderAsMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.HEADER_NAME, "   ");
        AtomicReference<String> seenInsideChain = new AtomicReference<>();

        filter.doFilter(request, new MockHttpServletResponse(), captureRequestId(seenInsideChain));

        assertThat(seenInsideChain.get()).isNotBlank();
        assertThat(seenInsideChain.get()).isNotEqualTo("   ");
    }

    @DisplayName("요청이 끝나면 MDC를 비워 다음 요청에 값이 새지 않는다")
    @Test
    void clearMdcAfterRequest() throws Exception {
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), (req, res) -> {
        });

        assertThat(RequestIdFilter.current()).isNull();
    }

    @DisplayName("체인에서 예외가 나도 MDC를 비운다")
    @Test
    void clearMdcWhenChainFails() {
        FilterChain failing = (request, response) -> {
            throw new IllegalStateException("boom");
        };

        assertThatThrownBy(() -> filter.doFilter(
                new MockHttpServletRequest(), new MockHttpServletResponse(), failing
        )).isInstanceOf(IllegalStateException.class);

        assertThat(RequestIdFilter.current()).isNull();
    }

    private FilterChain captureRequestId(AtomicReference<String> holder) {
        return (request, response) -> holder.set(RequestIdFilter.current());
    }
}
