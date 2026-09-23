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

    // 전략
    STRATEGY_NOT_FOUND(HttpStatus.NOT_FOUND, "STRATEGY-404-001", "요청한 전략을 찾을 수 없습니다."),

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
    CANDLE_NOT_FOUND(HttpStatus.NOT_FOUND, "CANDLE-404-001", "요청한 분봉을 찾을 수 없습니다."),

    // 실시간 시세
    REALTIME_INVALID_STOMP_REQUEST(HttpStatus.BAD_REQUEST, "REALTIME-400-001", "허용되지 않은 실시간 구독 요청입니다."),

    // AI 전략 변환
    AI_TRANSLATION_INVALID_RESPONSE(HttpStatus.BAD_GATEWAY, "AI-502-001", "AI 서버 응답을 처리할 수 없습니다."),
    AI_TRANSLATION_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "AI-503-001", "AI 서버를 일시적으로 사용할 수 없습니다."),
    AI_TRANSLATION_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "AI-504-001", "AI 서버 응답이 지연되고 있습니다."),
    AI_TRANSLATION_FAILED(HttpStatus.UNPROCESSABLE_ENTITY, "AI-422-001", "전략 설명을 전략으로 바꾸지 못했습니다."),
    AI_TRANSLATION_QUOTA_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "AI-429-001", "전략 변환 요청이 너무 잦습니다. 잠시 후 다시 시도해 주세요."),

    // 백테스트
    BACKTEST_INVALID_PERIOD(HttpStatus.BAD_REQUEST, "BACKTEST-400-001", "백테스트 기간이 올바르지 않습니다."),
    BACKTEST_UNSUPPORTED_QUOTE_CURRENCY(HttpStatus.BAD_REQUEST, "BACKTEST-400-002", "현재 지원하지 않는 기준 통화입니다."),
    BACKTEST_INVALID_INITIAL_CAPITAL(HttpStatus.BAD_REQUEST, "BACKTEST-400-003", "초기 자본은 0보다 커야 합니다."),
    BACKTEST_INVALID_COST(HttpStatus.BAD_REQUEST, "BACKTEST-400-004", "수수료와 슬리피지는 음수일 수 없습니다."),
    BACKTEST_INVALID_CONFIGURATION(HttpStatus.BAD_REQUEST, "BACKTEST-400-005", "백테스트 실행 설정이 올바르지 않습니다."),
    BACKTEST_NOT_FOUND(HttpStatus.NOT_FOUND, "BACKTEST-404-001", "요청한 백테스트 실행을 찾을 수 없습니다."),

    // 모의투자
    SIMULATION_INVALID_CONFIGURATION(HttpStatus.BAD_REQUEST, "SIMULATION-400-001", "모의투자 실행 설정이 올바르지 않습니다."),
    SIMULATION_NOT_FOUND(HttpStatus.NOT_FOUND, "SIMULATION-404-001", "요청한 모의투자 실행을 찾을 수 없습니다."),
    SIMULATION_INVALID_STATUS_TRANSITION(HttpStatus.CONFLICT, "SIMULATION-409-001", "현재 모의투자 실행 상태에서는 요청한 상태로 변경할 수 없습니다."),

    // 대회
    CONTEST_FACTOR_PERIOD_TOO_LONG(HttpStatus.BAD_REQUEST, "CONTEST-400-001", "대회 데이터의 워밍업보다 긴 팩터 기간은 제출할 수 없습니다."),
    CONTEST_ROUND_NOT_FOUND(HttpStatus.NOT_FOUND, "CONTEST-404-001", "요청한 대회 회차를 찾을 수 없습니다."),
    CONTEST_ENTRY_NOT_FOUND(HttpStatus.NOT_FOUND, "CONTEST-404-002", "이 회차에 제출한 전략이 없습니다."),
    CONTEST_SUBMISSION_CLOSED(HttpStatus.CONFLICT, "CONTEST-409-001", "지금은 대회 제출 기간이 아닙니다."),
    CONTEST_RESULT_NOT_PUBLISHED(HttpStatus.CONFLICT, "CONTEST-409-003", "대회 결과는 채점이 끝난 뒤에 공개됩니다."),

    // 알림
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "NOTIFICATION-404-001", "요청한 알림을 찾을 수 없습니다.");

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
