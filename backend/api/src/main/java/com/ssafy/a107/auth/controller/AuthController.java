package com.ssafy.a107.auth.controller;

import com.ssafy.a107.auth.controller.request.SsafyLoginRequest;
import com.ssafy.a107.auth.controller.response.LoginResponse;
import com.ssafy.a107.auth.controller.response.SsafyAuthorizeUrlResponse;
import com.ssafy.a107.auth.service.AuthCommandService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth/ssafy")
public class AuthController {

    private final AuthCommandService authCommandService;

    public AuthController(AuthCommandService authCommandService) {
        this.authCommandService = authCommandService;
    }

    /* 프론트가 사용자를 SSAFY 로그인 화면으로 보낼 때 쓰는 URL. */
    @GetMapping("/authorize-url")
    public SsafyAuthorizeUrlResponse getAuthorizeUrl() {
        return authCommandService.createAuthorizeUrl();
    }

    /* 인가 코드를 받아 서비스 액세스 토큰을 발급한다. 경로에 동사를 두지 않는 컨벤션에 맞춰 tokens 로 둔다. */
    @PostMapping("/tokens")
    public LoginResponse issueToken(@Valid @RequestBody SsafyLoginRequest request) {
        return authCommandService.login(request.authorizationCode());
    }
}
