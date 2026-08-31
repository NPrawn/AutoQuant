package com.ssafy.a107.auth.client;

import com.ssafy.a107.auth.config.SsafyOAuth2Properties;
import com.ssafy.a107.common.exception.ApiErrorCode;
import com.ssafy.a107.common.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class SsafyAuthRestClient implements SsafyAuthClient {

    private static final Logger log = LoggerFactory.getLogger(SsafyAuthRestClient.class);

    private static final String GRANT_TYPE_AUTHORIZATION_CODE = "authorization_code";
    private static final String BEARER_PREFIX = "Bearer ";

    private final RestClient restClient;
    private final SsafyOAuth2Properties properties;

    public SsafyAuthRestClient(
            @Qualifier("ssafyRestClient") RestClient restClient,
            SsafyOAuth2Properties properties
    ) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public String exchangeAuthorizationCode(String authorizationCode) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", GRANT_TYPE_AUTHORIZATION_CODE);
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        form.add("redirect_uri", properties.redirectUri());
        form.add("code", authorizationCode);

        SsafyTokenResponse response = execute(
                "token",
                // 인가 코드가 만료되거나 이미 쓰인 경우 SSAFY 가 4xx 를 준다. 사용자가 다시 로그인해야 한다.
                ApiErrorCode.AUTH_AUTHORIZATION_CODE_REJECTED,
                () -> restClient.post()
                        .uri(properties.tokenUrl())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(form)
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, (request, clientResponse) -> {
                            throw new SsafyResponseStatusException(clientResponse.getStatusCode());
                        })
                        .body(SsafyTokenResponse.class)
        );

        if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
            log.warn("SSAFY 토큰 응답에 access_token 이 없습니다.");
            throw new ApiException(ApiErrorCode.AUTH_PROVIDER_UNAVAILABLE);
        }
        return response.accessToken();
    }

    @Override
    public SsafyUserInfo fetchUserInfo(String ssafyAccessToken) {
        SsafyUserInfoResponse response = execute(
                "userInfo",
                // 방금 발급받은 토큰이 거부되는 상황은 사용자가 고칠 수 없다. 업스트림 문제로 다룬다.
                ApiErrorCode.AUTH_PROVIDER_UNAVAILABLE,
                () -> restClient.get()
                        .uri(properties.userInfoUrl())
                        .header(HttpHeaders.AUTHORIZATION, BEARER_PREFIX + ssafyAccessToken)
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, (request, clientResponse) -> {
                            throw new SsafyResponseStatusException(clientResponse.getStatusCode());
                        })
                        .body(SsafyUserInfoResponse.class)
        );

        if (response == null || isBlank(response.userId())) {
            log.warn("SSAFY 사용자 정보 응답에 userId 가 없습니다.");
            throw new ApiException(ApiErrorCode.AUTH_PROVIDER_UNAVAILABLE);
        }
        if (isBlank(response.email()) || isBlank(response.name())) {
            // email 과 name 은 사용자가 필수 동의를 한 경우에만 내려온다.
            throw new ApiException(ApiErrorCode.AUTH_CONSENT_REQUIRED);
        }
        return new SsafyUserInfo(response.userId(), response.email(), response.name());
    }

    /* SSAFY 응답 본문과 예외 원문을 밖으로 내보내지 않고 정의된 오류 코드로 바꾼다. */
    private <T> T execute(String callName, ApiErrorCode errorStatusCode, SsafyCall<T> call) {
        try {
            return call.execute();
        } catch (SsafyResponseStatusException exception) {
            HttpStatusCode statusCode = exception.getStatusCode();
            log.warn("SSAFY {} 호출이 실패했습니다. status={}", callName, statusCode.value());
            if (statusCode.is5xxServerError()) {
                throw new ApiException(ApiErrorCode.AUTH_PROVIDER_UNAVAILABLE, exception);
            }
            throw new ApiException(errorStatusCode, exception);
        } catch (RestClientException exception) {
            // 연결 실패, 타임아웃, 역직렬화 실패
            log.warn("SSAFY {} 호출에서 통신 오류가 발생했습니다.", callName);
            throw new ApiException(ApiErrorCode.AUTH_PROVIDER_UNAVAILABLE, exception);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @FunctionalInterface
    private interface SsafyCall<T> {
        T execute();
    }

    /* 상태 코드만 전달하기 위한 내부 예외. 응답 본문은 담지 않는다. */
    private static class SsafyResponseStatusException extends RestClientException {

        private final HttpStatusCode statusCode;

        SsafyResponseStatusException(HttpStatusCode statusCode) {
            super("SSAFY responded with status " + statusCode.value());
            this.statusCode = statusCode;
        }

        HttpStatusCode getStatusCode() {
            return statusCode;
        }
    }
}
