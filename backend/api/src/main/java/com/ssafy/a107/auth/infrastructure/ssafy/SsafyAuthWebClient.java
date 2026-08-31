package com.ssafy.a107.auth.infrastructure.ssafy;

import com.ssafy.a107.auth.config.SsafyOAuth2Properties;
import com.ssafy.a107.auth.service.port.SsafyAuthClient;
import com.ssafy.a107.auth.service.port.SsafyUserInfo;
import com.ssafy.a107.common.exception.ApiErrorCode;
import com.ssafy.a107.common.exception.ApiException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.function.Supplier;

@Component
public class SsafyAuthWebClient implements SsafyAuthClient {

    private static final String GRANT_TYPE_AUTHORIZATION_CODE = "authorization_code";
    private static final String BEARER_PREFIX = "Bearer ";

    private final WebClient webClient;
    private final SsafyOAuth2Properties properties;

    public SsafyAuthWebClient(
            @Qualifier("ssafyWebClient") WebClient webClient,
            SsafyOAuth2Properties properties
    ) {
        this.webClient = webClient;
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

        // 인가 코드가 만료되거나 이미 쓰인 경우 SSAFY 가 4xx 를 준다. 사용자가 다시 로그인해야 한다.
        SsafyTokenResponse response = execute(
                ApiErrorCode.AUTH_AUTHORIZATION_CODE_REJECTED,
                () -> webClient.post()
                        .uri(properties.tokenPath())
                        .body(BodyInserters.fromFormData(form))
                        .retrieve()
                        .bodyToMono(SsafyTokenResponse.class)
                        .timeout(properties.timeout())
                        .block()
        );

        if (response == null || isBlank(response.accessToken())) {
            throw new ApiException(ApiErrorCode.AUTH_PROVIDER_UNAVAILABLE);
        }
        return response.accessToken();
    }

    @Override
    public SsafyUserInfo fetchUserInfo(String ssafyAccessToken) {
        // 방금 발급받은 토큰이 거부되는 상황은 사용자가 고칠 수 없다. 업스트림 문제로 다룬다.
        SsafyUserInfoResponse response = execute(
                ApiErrorCode.AUTH_PROVIDER_UNAVAILABLE,
                () -> webClient.get()
                        .uri(properties.userInfoPath())
                        .header(HttpHeaders.AUTHORIZATION, BEARER_PREFIX + ssafyAccessToken)
                        .retrieve()
                        .bodyToMono(SsafyUserInfoResponse.class)
                        .timeout(properties.timeout())
                        .block()
        );

        if (response == null || isBlank(response.userId())) {
            throw new ApiException(ApiErrorCode.AUTH_PROVIDER_UNAVAILABLE);
        }
        if (isBlank(response.email()) || isBlank(response.name())) {
            // email 과 name 은 사용자가 필수 동의를 한 경우에만 내려온다.
            throw new ApiException(ApiErrorCode.AUTH_CONSENT_REQUIRED);
        }
        return new SsafyUserInfo(response.userId(), response.email(), response.name());
    }

    /* SSAFY 응답 본문과 예외 원문을 밖으로 내보내지 않고 정의된 오류 코드로 바꾼다. */
    private <T> T execute(ApiErrorCode clientErrorCode, Supplier<T> call) {
        try {
            return call.get();
        } catch (ApiException exception) {
            throw exception;
        } catch (WebClientResponseException exception) {
            if (exception.getStatusCode().is5xxServerError()) {
                throw new ApiException(ApiErrorCode.AUTH_PROVIDER_UNAVAILABLE, exception);
            }
            throw new ApiException(clientErrorCode, exception);
        } catch (RuntimeException exception) {
            // 연결 실패, 타임아웃, 역직렬화 실패를 모두 업스트림 장애로 다룬다.
            throw new ApiException(ApiErrorCode.AUTH_PROVIDER_UNAVAILABLE, exception);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
