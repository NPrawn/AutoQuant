package com.ssafy.a107.auth.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.a107.common.exception.ApiErrorCode;
import com.ssafy.a107.common.exception.response.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/* 인증 실패는 컨트롤러에 닿지 못해 ApiExceptionHandler 를 거치지 않는다. 응답 형식을 맞추려고 여기서 직접 쓴다. */
@Component
public class AuthenticationErrorWriter {

    private final ObjectMapper objectMapper;

    public AuthenticationErrorWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletRequest request, HttpServletResponse response, ApiErrorCode errorCode)
            throws IOException {
        if (response.isCommitted()) {
            return;
        }

        ApiErrorResponse body = ApiErrorResponse.of(errorCode, request.getRequestURI());

        response.setStatus(errorCode.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), body);
    }
}
