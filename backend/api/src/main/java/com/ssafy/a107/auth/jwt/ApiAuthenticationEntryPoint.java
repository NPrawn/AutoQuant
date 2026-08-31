package com.ssafy.a107.auth.jwt;

import com.ssafy.a107.common.exception.ApiErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/* 인증 없이 보호된 경로를 호출한 경우. */
@Component
public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final AuthenticationErrorWriter errorWriter;

    public ApiAuthenticationEntryPoint(AuthenticationErrorWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authenticationException
    ) throws IOException {
        errorWriter.write(request, response, ApiErrorCode.AUTH_CREDENTIAL_REQUIRED);
    }
}
