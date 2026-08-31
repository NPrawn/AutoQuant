package com.ssafy.a107.common.exception.response;

import com.ssafy.a107.common.exception.ApiErrorCode;

import java.time.Instant;
import java.util.List;

public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        List<ValidationError> validationErrors
) {

    public ApiErrorResponse {
        validationErrors = List.copyOf(validationErrors);
    }

    public static ApiErrorResponse of(ApiErrorCode errorCode, String path) {
        return of(errorCode, path, List.of());
    }

    public static ApiErrorResponse of(
            ApiErrorCode errorCode,
            String path,
            List<ValidationError> validationErrors
    ) {
        return new ApiErrorResponse(
                Instant.now(),
                errorCode.getHttpStatus().value(),
                errorCode.getCode(),
                errorCode.getMessage(),
                path,
                validationErrors
        );
    }

    public record ValidationError(String field, String reason) {
    }
}
