package com.ssafy.a107.common.exception;

import org.springframework.http.HttpStatus;

public enum ApiErrorCode {

    // 공통
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "COMMON-400-001", "요청 값이 올바르지 않습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "COMMON-405-001", "지원하지 않는 HTTP 메서드입니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON-404-001", "요청한 리소스를 찾을 수 없습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON-500-001", "서버 내부 오류가 발생했습니다."),

    // 인증
    AUTH_INVALID_AUTHORIZATION_CODE(HttpStatus.BAD_REQUEST, "AUTH-400-001", "인가 코드가 올바르지 않습니다."),
    AUTH_CONSENT_REQUIRED(HttpStatus.BAD_REQUEST, "AUTH-400-002", "로그인에 필요한 정보 제공 동의가 필요합니다."),
    AUTH_AUTHORIZATION_CODE_REJECTED(HttpStatus.UNAUTHORIZED, "AUTH-401-001", "인가 코드가 만료되었거나 이미 사용되었습니다."),
    AUTH_CREDENTIAL_REQUIRED(HttpStatus.UNAUTHORIZED, "AUTH-401-002", "인증이 필요합니다."),
    AUTH_INVALID_CREDENTIAL(HttpStatus.UNAUTHORIZED, "AUTH-401-003", "인증 정보가 유효하지 않습니다."),
    AUTH_MEMBER_NOT_ACTIVE(HttpStatus.FORBIDDEN, "AUTH-403-001", "로그인할 수 없는 회원 상태입니다."),
    AUTH_PROVIDER_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "AUTH-502-001", "SSAFY 인증 서버와 통신할 수 없습니다."),

    // 회원
    MEMBER_INVALID_LOGIN_IDENTITY(HttpStatus.BAD_REQUEST, "MEMBER-400-001", "로그인 제공자 식별 정보가 올바르지 않습니다."),
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "MEMBER-404-001", "요청한 회원을 찾을 수 없습니다."),
    MEMBER_INVALID_PROFILE(HttpStatus.BAD_REQUEST, "MEMBER-400-002", "회원 프로필 정보가 올바르지 않습니다."),
    MEMBER_INVALID_STATUS_TRANSITION(HttpStatus.CONFLICT, "MEMBER-409-001", "현재 회원 상태에서는 요청한 상태로 변경할 수 없습니다."),

    // 자산
    ASSET_INVALID_MARKET_INFORMATION(HttpStatus.BAD_REQUEST, "ASSET-400-001", "거래 자산의 마켓 정보가 올바르지 않습니다."),
    ASSET_INVALID_TRADING_PERIOD(HttpStatus.BAD_REQUEST, "ASSET-400-002", "거래지원 기간이 올바르지 않습니다."),
    ASSET_INVALID_MARKET_CODE(HttpStatus.BAD_REQUEST, "ASSET-400-003", "거래소 마켓 코드 형식이 올바르지 않습니다."),
    ASSET_UNSUPPORTED_QUOTE_CURRENCY(HttpStatus.BAD_REQUEST, "ASSET-400-004", "현재 지원하지 않는 가격 표시 통화입니다."),
    ASSET_NOT_SUPPORTED_BY_EXCHANGE(HttpStatus.NOT_FOUND, "ASSET-404-001", "거래소에서 지원하는 종목을 찾을 수 없습니다."),
    ASSET_INVALID_STATUS_TRANSITION(HttpStatus.CONFLICT, "ASSET-409-001", "현재 거래지원 상태에서는 요청한 상태로 변경할 수 없습니다."),
    ASSET_ALREADY_EXISTS(HttpStatus.CONFLICT, "ASSET-409-002", "이미 등록된 거래 자산입니다."),
    ASSET_EXCHANGE_INVALID_RESPONSE(HttpStatus.BAD_GATEWAY, "ASSET-502-001", "거래소 응답을 처리할 수 없습니다."),
    ASSET_EXCHANGE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "ASSET-503-001", "거래소 정보를 일시적으로 조회할 수 없습니다."),

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
