package com.ssafy.a107.common.exception;

import com.ssafy.a107.common.exception.response.ApiErrorResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler exceptionHandler = new ApiExceptionHandler();

    @DisplayName("API 예외를 에러 코드에 정의된 응답으로 변환한다")
    @Test
    void handleApiException() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/candles");
        ApiException exception = new ApiException(ApiErrorCode.CANDLE_NOT_FOUND);

        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleApiException(
                exception,
                request
        );

        assertThat(response.getStatusCode()).isEqualTo(ApiErrorCode.CANDLE_NOT_FOUND.getHttpStatus());
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(404);
        assertThat(response.getBody().code()).isEqualTo("CANDLE-404-001");
        assertThat(response.getBody().message()).isEqualTo("요청한 분봉을 찾을 수 없습니다.");
        assertThat(response.getBody().path()).isEqualTo("/api/candles");
        assertThat(response.getBody().validationErrors()).isEmpty();
    }
}
