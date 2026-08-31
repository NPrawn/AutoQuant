package com.ssafy.a107.auth.infrastructure.ssafy;

import com.ssafy.a107.auth.config.SsafyOAuth2Properties;
import com.ssafy.a107.auth.service.port.SsafyUserInfo;
import com.ssafy.a107.common.exception.ApiErrorCode;
import com.ssafy.a107.common.exception.ApiException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SsafyAuthWebClientTest {

    private static final String TOKEN_PATH = "/ssafy/oauth2/token";
    private static final String USER_INFO_PATH = "/ssafy/resources/userInfo";

    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private final AtomicReference<String> authorizationHeader = new AtomicReference<>();
    private HttpServer httpServer;

    @AfterEach
    void tearDown() {
        if (httpServer != null) {
            httpServer.stop(0);
        }
    }

    @DisplayName("인가 코드를 SSAFY 액세스 토큰으로 교환한다")
    @Test
    void exchangeAuthorizationCode() throws IOException {
        SsafyAuthWebClient client = createClient(TOKEN_PATH, HttpStatus.OK, """
                {
                  "token_type": "bearer",
                  "access_token": "ssafy-access-token",
                  "expires_in": "3600",
                  "scope": "user"
                }
                """);

        assertThat(client.exchangeAuthorizationCode("authorization-code")).isEqualTo("ssafy-access-token");
        assertThat(requestBody.get())
                .contains("grant_type=authorization_code")
                .contains("client_id=client-id")
                .contains("code=authorization-code");
    }

    @DisplayName("만료된 인가 코드는 다시 로그인해야 하는 오류로 번역한다")
    @Test
    void translateRejectedAuthorizationCode() throws IOException {
        SsafyAuthWebClient client = createClient(TOKEN_PATH, HttpStatus.BAD_REQUEST, "{}");

        assertThatThrownBy(() -> client.exchangeAuthorizationCode("expired-code"))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ApiErrorCode.AUTH_AUTHORIZATION_CODE_REJECTED));
    }

    @DisplayName("SSAFY 서버 장애는 업스트림 오류로 번역한다")
    @Test
    void translateProviderServerError() throws IOException {
        SsafyAuthWebClient client = createClient(TOKEN_PATH, HttpStatus.SERVICE_UNAVAILABLE, "{}");

        assertThatThrownBy(() -> client.exchangeAuthorizationCode("authorization-code"))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ApiErrorCode.AUTH_PROVIDER_UNAVAILABLE));
    }

    @DisplayName("액세스 토큰이 비어 있는 응답은 업스트림 오류로 다룬다")
    @Test
    void rejectResponseWithoutAccessToken() throws IOException {
        SsafyAuthWebClient client = createClient(TOKEN_PATH, HttpStatus.OK, "{\"token_type\":\"bearer\"}");

        assertThatThrownBy(() -> client.exchangeAuthorizationCode("authorization-code"))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ApiErrorCode.AUTH_PROVIDER_UNAVAILABLE));
    }

    @DisplayName("액세스 토큰을 Bearer 헤더로 보내 사용자 정보를 조회한다")
    @Test
    void fetchUserInfo() throws IOException {
        SsafyAuthWebClient client = createClient(USER_INFO_PATH, HttpStatus.OK, """
                {
                  "userId": "1234567",
                  "email": "gaon@ssafy.com",
                  "name": "가온"
                }
                """);

        SsafyUserInfo userInfo = client.fetchUserInfo("ssafy-access-token");

        assertThat(userInfo).isEqualTo(new SsafyUserInfo("1234567", "gaon@ssafy.com", "가온"));
        assertThat(authorizationHeader.get()).isEqualTo("Bearer ssafy-access-token");
    }

    @DisplayName("이메일이 없는 사용자 정보는 동의가 필요한 오류로 다룬다")
    @Test
    void rejectUserInfoWithoutEmail() throws IOException {
        SsafyAuthWebClient client = createClient(
                USER_INFO_PATH,
                HttpStatus.OK,
                "{\"userId\":\"1234567\",\"name\":\"가온\"}"
        );

        assertThatThrownBy(() -> client.fetchUserInfo("ssafy-access-token"))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ApiErrorCode.AUTH_CONSENT_REQUIRED));
    }

    @DisplayName("이름이 없는 사용자 정보는 동의가 필요한 오류로 다룬다")
    @Test
    void rejectUserInfoWithoutName() throws IOException {
        SsafyAuthWebClient client = createClient(
                USER_INFO_PATH,
                HttpStatus.OK,
                "{\"userId\":\"1234567\",\"email\":\"gaon@ssafy.com\"}"
        );

        assertThatThrownBy(() -> client.fetchUserInfo("ssafy-access-token"))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ApiErrorCode.AUTH_CONSENT_REQUIRED));
    }

    private SsafyAuthWebClient createClient(String path, HttpStatus status, String responseBody) throws IOException {
        httpServer = HttpServer.create(
                new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
                0
        );
        httpServer.createContext(path, exchange -> respond(exchange, status, responseBody));
        httpServer.start();

        String baseUrl = "http://127.0.0.1:" + httpServer.getAddress().getPort();
        return new SsafyAuthWebClient(WebClient.create(baseUrl), properties(baseUrl));
    }

    private void respond(HttpExchange exchange, HttpStatus status, String responseBody) throws IOException {
        authorizationHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
        requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));

        byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        exchange.sendResponseHeaders(status.value(), body.length);
        try (var response = exchange.getResponseBody()) {
            response.write(body);
        }
    }

    private static SsafyOAuth2Properties properties(String baseUrl) {
        return new SsafyOAuth2Properties(
                baseUrl,
                "client-id",
                "client-secret",
                "http://localhost:5173/auth/ssafy/callback",
                "/oauth/sso-check",
                TOKEN_PATH,
                USER_INFO_PATH,
                Duration.ofSeconds(5)
        );
    }
}
