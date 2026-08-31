package com.ssafy.a107.auth.client;

import com.ssafy.a107.auth.config.SsafyOAuth2Properties;
import com.ssafy.a107.common.exception.ApiErrorCode;
import com.ssafy.a107.common.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SsafyAuthRestClientTest {

    private static final String TOKEN_URL = "https://project.ssafy.com/ssafy/oauth2/token";
    private static final String USER_INFO_URL = "https://project.ssafy.com/ssafy/resources/userInfo";

    private MockRestServiceServer ssafyServer;
    private SsafyAuthRestClient ssafyAuthClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        ssafyServer = MockRestServiceServer.bindTo(builder).build();
        ssafyAuthClient = new SsafyAuthRestClient(builder.build(), properties());
    }

    @DisplayName("인가 코드를 SSAFY 액세스 토큰으로 교환한다")
    @Test
    void exchangeAuthorizationCode() {
        ssafyServer.expect(requestTo(TOKEN_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("grant_type=authorization_code")))
                .andRespond(withSuccess("""
                        {
                          "token_type": "bearer",
                          "access_token": "ssafy-access-token",
                          "expires_in": "3600",
                          "scope": "user"
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThat(ssafyAuthClient.exchangeAuthorizationCode("authorization-code"))
                .isEqualTo("ssafy-access-token");
        ssafyServer.verify();
    }

    @DisplayName("만료된 인가 코드는 다시 로그인해야 하는 오류로 번역한다")
    @Test
    void translateRejectedAuthorizationCode() {
        ssafyServer.expect(requestTo(TOKEN_URL)).andRespond(withBadRequest());

        assertThatThrownBy(() -> ssafyAuthClient.exchangeAuthorizationCode("expired-code"))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ApiErrorCode.AUTH_AUTHORIZATION_CODE_REJECTED));
    }

    @DisplayName("SSAFY 서버 장애는 업스트림 오류로 번역한다")
    @Test
    void translateProviderServerError() {
        ssafyServer.expect(requestTo(TOKEN_URL)).andRespond(withServerError());

        assertThatThrownBy(() -> ssafyAuthClient.exchangeAuthorizationCode("authorization-code"))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ApiErrorCode.AUTH_PROVIDER_UNAVAILABLE));
    }

    @DisplayName("액세스 토큰이 비어 있는 응답은 업스트림 오류로 다룬다")
    @Test
    void rejectResponseWithoutAccessToken() {
        ssafyServer.expect(requestTo(TOKEN_URL))
                .andRespond(withSuccess("{\"token_type\":\"bearer\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> ssafyAuthClient.exchangeAuthorizationCode("authorization-code"))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ApiErrorCode.AUTH_PROVIDER_UNAVAILABLE));
    }

    @DisplayName("액세스 토큰을 Bearer 헤더로 보내 사용자 정보를 조회한다")
    @Test
    void fetchUserInfo() {
        ssafyServer.expect(requestTo(USER_INFO_URL))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer ssafy-access-token"))
                .andRespond(withSuccess("""
                        {
                          "userId": "1234567",
                          "email": "gaon@ssafy.com",
                          "name": "가온"
                        }
                        """, MediaType.APPLICATION_JSON));

        SsafyUserInfo userInfo = ssafyAuthClient.fetchUserInfo("ssafy-access-token");

        assertThat(userInfo).isEqualTo(new SsafyUserInfo("1234567", "gaon@ssafy.com", "가온"));
        ssafyServer.verify();
    }

    @DisplayName("이메일이 없는 사용자 정보는 동의가 필요한 오류로 다룬다")
    @Test
    void rejectUserInfoWithoutEmail() {
        ssafyServer.expect(requestTo(USER_INFO_URL))
                .andRespond(withSuccess("{\"userId\":\"1234567\",\"name\":\"가온\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> ssafyAuthClient.fetchUserInfo("ssafy-access-token"))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ApiErrorCode.AUTH_CONSENT_REQUIRED));
    }

    @DisplayName("이름이 없는 사용자 정보는 동의가 필요한 오류로 다룬다")
    @Test
    void rejectUserInfoWithoutName() {
        ssafyServer.expect(requestTo(USER_INFO_URL))
                .andRespond(withSuccess(
                        "{\"userId\":\"1234567\",\"email\":\"gaon@ssafy.com\"}",
                        MediaType.APPLICATION_JSON
                ));

        assertThatThrownBy(() -> ssafyAuthClient.fetchUserInfo("ssafy-access-token"))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ApiErrorCode.AUTH_CONSENT_REQUIRED));
    }

    private static SsafyOAuth2Properties properties() {
        return new SsafyOAuth2Properties(
                "client-id",
                "client-secret",
                "http://localhost:5173/auth/ssafy/callback",
                "https://project.ssafy.com/oauth/sso-check",
                TOKEN_URL,
                USER_INFO_URL,
                Duration.ofSeconds(3),
                Duration.ofSeconds(5)
        );
    }
}
