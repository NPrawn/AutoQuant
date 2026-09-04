package com.ssafy.a107.common.exception;

import com.ssafy.a107.common.exception.response.ApiErrorResponse;
import jakarta.validation.ConstraintViolationException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> handleApiException(
            ApiException exception,
            HttpServletRequest request
    ) {
        return createResponse(exception.getErrorCode(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        List<ApiErrorResponse.ValidationError> validationErrors = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::toValidationError)
                .toList();

        return createResponse(ApiErrorCode.INVALID_REQUEST, request, validationErrors);
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            ConstraintViolationException.class
    })
    public ResponseEntity<ApiErrorResponse> handleInvalidRequest(
            Exception exception,
            HttpServletRequest request
    ) {
        return createResponse(ApiErrorCode.INVALID_REQUEST, request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException exception,
            HttpServletRequest request
    ) {
        return createResponse(ApiErrorCode.METHOD_NOT_ALLOWED, request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleResourceNotFound(
            NoResourceFoundException exception,
            HttpServletRequest request
    ) {
        return createResponse(ApiErrorCode.RESOURCE_NOT_FOUND, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpectedException(
            Exception exception,
            HttpServletRequest request
    ) {
        log.error("Unhandled API exception", exception);
        return createResponse(ApiErrorCode.INTERNAL_SERVER_ERROR, request);
    }

    private ApiErrorResponse.ValidationError toValidationError(FieldError fieldError) {
        return new ApiErrorResponse.ValidationError(
                fieldError.getField(),
                fieldError.getDefaultMessage()
        );
    }

    private ResponseEntity<ApiErrorResponse> createResponse(
            ApiErrorCode errorCode,
            HttpServletRequest request
    ) {
        return createResponse(errorCode, request, List.of());
    }

    private ResponseEntity<ApiErrorResponse> createResponse(
            ApiErrorCode errorCode,
            HttpServletRequest request,
            List<ApiErrorResponse.ValidationError> validationErrors
    ) {
        ApiErrorResponse response = ApiErrorResponse.of(
                errorCode,
                request.getRequestURI(),
                validationErrors
        );

        return ResponseEntity.status(errorCode.getHttpStatus()).body(response);
    }
}
