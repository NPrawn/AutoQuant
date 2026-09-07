package com.ssafy.a107.auth.controller;

import com.ssafy.a107.auth.config.AuthLoginProperties;
import com.ssafy.a107.auth.config.JwtProperties;
import com.ssafy.a107.auth.controller.response.SsafyAuthorizeUrlResponse;
import com.ssafy.a107.auth.jwt.AccessTokenCookie;
import com.ssafy.a107.auth.jwt.IssuedAccessToken;
import com.ssafy.a107.auth.service.AuthCommandService;
import com.ssafy.a107.common.exception.ApiException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthCommandService authCommandService;
    private final AuthLoginProperties loginProperties;
    private final JwtProperties jwtProperties;

    public AuthController(
            AuthCommandService authCommandService,
            AuthLoginProperties loginProperties,
            JwtProperties jwtProperties
    ) {
        this.authCommandService = authCommandService;
        this.loginProperties = loginProperties;
        this.jwtProperties = jwtProperties;
    }

    /* 프론트가 사용자를 SSAFY 로그인 화면으로 보낼 때 쓰는 URL. */
    @GetMapping("/ssafy/authorize-url")
    public SsafyAuthorizeUrlResponse getAuthorizeUrl() {
        return authCommandService.createAuthorizeUrl();
    }

    /*
     * SSAFY 가 인가 코드를 들고 브라우저를 보내는 콜백. 응답이 JSON 이면 사용자가 죽은 페이지에 갇히므로
     * 성공이든 실패든 프론트로 302 리다이렉트한다. 실패는 공통 핸들러 대신 여기서 오류 코드를 쿼리로 넘긴다.
     */
    @GetMapping("/ssafy/callback")
    public ResponseEntity<Void> completeLogin(
            @RequestParam(name = "code", required = false) String authorizationCode
    ) {
        IssuedAccessToken accessToken;
        try {
            accessToken = authCommandService.login(authorizationCode);
        } catch (ApiException exception) {
            return redirect(failureLocation(exception));
        }

        // 토큰은 응답 본문에 담지 않는다. httpOnly 쿠키라 브라우저 JS 가 읽을 수 없다.
        ResponseCookie cookie = AccessTokenCookie.issued(
                accessToken.value(),
                loginProperties.cookieSecure(),
                jwtProperties.accessTokenValidity()
        );

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(loginProperties.frontendBaseUrl()))
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .build();
    }

    /*
     * 토큰 쿠키를 만료시킨다. 서버는 상태를 두지 않으므로 지울 대상은 쿠키뿐이고, 인증 여부와 무관하게
     * 같은 응답을 준다. 로그인 제공자와 관계없는 동작이라 SSAFY 경로 아래에 두지 않는다.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        ResponseCookie cookie = AccessTokenCookie.expired(loginProperties.cookieSecure());

        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .build();
    }

    private ResponseEntity<Void> redirect(URI location) {
        return ResponseEntity.status(HttpStatus.FOUND).location(location).build();
    }

    private URI failureLocation(ApiException exception) {
        return UriComponentsBuilder.fromUriString(loginProperties.frontendBaseUrl())
                .path("/login")
                .queryParam("error", exception.getErrorCode().getCode())
                .build()
                .toUri();
    }
}
