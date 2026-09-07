package com.ssafy.a107.common.logging;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
        String generated = requestIdFor("   ");

        assertThat(generated).isNotBlank();
        assertThat(generated).isNotEqualTo("   ");
    }

    @DisplayName("형식에 맞지 않는 값은 이어받지 않고 새로 만든다")
    @ValueSource(strings = {
            "inj	FAKE-LINE-INJECTED",
            "req 1234",
            "[req]",
            "req\"1234",
            "req,1234",
    })
    @ParameterizedTest
    void rejectRequestIdWithUnsafeCharacters(String incoming) throws Exception {
        String used = requestIdFor(incoming);

        assertThat(used).isNotEqualTo(incoming);
        assertThat(UUID.fromString(used)).isNotNull();
    }

    @DisplayName("64자를 넘는 값은 이어받지 않고 새로 만든다")
    @Test
    void rejectRequestIdOverTheLengthLimit() throws Exception {
        String tooLong = "a".repeat(65);

        String used = requestIdFor(tooLong);

        assertThat(used).isNotEqualTo(tooLong);
        assertThat(UUID.fromString(used)).isNotNull();
    }

    @DisplayName("64자까지는 그대로 이어받는다")
    @Test
    void acceptRequestIdAtTheLengthLimit() throws Exception {
        String atLimit = "a".repeat(64);

        assertThat(requestIdFor(atLimit)).isEqualTo(atLimit);
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

    private String requestIdFor(String incomingHeader) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.HEADER_NAME, incomingHeader);
        AtomicReference<String> seenInsideChain = new AtomicReference<>();

        filter.doFilter(request, new MockHttpServletResponse(), captureRequestId(seenInsideChain));

        return seenInsideChain.get();
    }

    private FilterChain captureRequestId(AtomicReference<String> holder) {
        return (request, response) -> holder.set(RequestIdFilter.current());
    }
}
