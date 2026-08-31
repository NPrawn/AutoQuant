package com.ssafy.a107.common.exception;

import org.springframework.http.HttpStatus;

public enum ApiErrorCode {

    // 공통
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "COMMON-400-001", "요청 값이 올바르지 않습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "COMMON-405-001", "지원하지 않는 HTTP 메서드입니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON-404-001", "요청한 리소스를 찾을 수 없습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON-500-001", "서버 내부 오류가 발생했습니다."),

    // 캔들
    CANDLE_INVALID_INTERVAL(HttpStatus.BAD_REQUEST, "CANDLE-400-001", "분봉 간격은 1 이상 32767 이하여야 합니다."),
    CANDLE_NOT_FOUND(HttpStatus.NOT_FOUND, "CANDLE-404-001", "요청한 분봉을 찾을 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    ApiErrorCode(HttpStatus httpStatus, String code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
