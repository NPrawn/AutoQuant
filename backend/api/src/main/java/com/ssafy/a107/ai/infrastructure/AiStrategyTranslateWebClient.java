package com.ssafy.a107.ai.infrastructure;

import com.ssafy.a107.ai.service.port.AiStrategyTranslation;
import com.ssafy.a107.ai.service.port.AiStrategyTranslator;
import com.ssafy.a107.common.exception.ApiErrorCode;
import com.ssafy.a107.common.exception.ApiException;
import com.ssafy.a107.strategy.domain.dsl.StrategyDsl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;
import java.util.concurrent.TimeoutException;

@Component
public class AiStrategyTranslateWebClient implements AiStrategyTranslator {

    private static final String TRANSLATE_PATH = "/api/v1/ai/strategies/translate";
    private static final Logger log = LoggerFactory.getLogger(AiStrategyTranslateWebClient.class);

    private final WebClient webClient;
    private final AiProperties properties;

    public AiStrategyTranslateWebClient(
            @Qualifier("aiWebClient") WebClient webClient,
            AiProperties properties
    ) {
        this.webClient = webClient;
        this.properties = properties;
    }

    @Override
    public AiStrategyTranslation translate(String userInput, StrategyDsl currentStrategy) {
        AiTranslateResponse response = requestTranslation(new AiTranslateRequest(userInput, currentStrategy));

        if (response == null || response.meta() == null) {
            throw new ApiException(ApiErrorCode.AI_TRANSLATION_INVALID_RESPONSE);
        }
        AiTranslateResponse.Meta meta = response.meta();
        if (meta.attempts() == null || meta.elapsedMs() == null) {
            throw new ApiException(ApiErrorCode.AI_TRANSLATION_INVALID_RESPONSE);
        }

        return new AiStrategyTranslation(
                response.strategy(),
                meta.attempts(),
                meta.model(),
                meta.elapsedMs(),
                orEmpty(meta.warnings()),
                orEmpty(meta.assumptions()),
                orEmpty(meta.changes())
        );
    }

    private AiTranslateResponse requestTranslation(AiTranslateRequest request) {
        try {
            return webClient.post()
                    .uri(TRANSLATE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(AiTranslateResponse.class)
                    .timeout(properties.timeout())
                    .block();
        } catch (WebClientResponseException exception) {
            throw new ApiException(toErrorCode(exception), exception);
        } catch (WebClientException exception) {
            throw new ApiException(ApiErrorCode.AI_TRANSLATION_UNAVAILABLE, exception);
        } catch (RuntimeException exception) {
            if (hasTimeoutCause(exception)) {
                throw new ApiException(ApiErrorCode.AI_TRANSLATION_TIMEOUT, exception);
            }
            throw new ApiException(ApiErrorCode.AI_TRANSLATION_INVALID_RESPONSE, exception);
        }
    }

    /*
     * AI 서버의 code·message·detail 은 밖으로 흘리지 않는다. 한 서버가 두 오류 형식으로 말하게 된다.
     * 진단에 필요한 detail(attempts, lastErrors)은 응답 대신 로그에 남긴다.
     */
    private ApiErrorCode toErrorCode(WebClientResponseException exception) {
        log.warn(
                "ai_translate_failed status={} body={}",
                exception.getStatusCode().value(),
                exception.getResponseBodyAsString()
        );

        return switch (exception.getStatusCode().value()) {
            case 400 -> ApiErrorCode.INVALID_REQUEST;
            case 422 -> ApiErrorCode.AI_TRANSLATION_FAILED;
            case 429 -> ApiErrorCode.AI_TRANSLATION_QUOTA_EXCEEDED;
            case 502, 503 -> ApiErrorCode.AI_TRANSLATION_UNAVAILABLE;
            case 504 -> ApiErrorCode.AI_TRANSLATION_TIMEOUT;
            default -> ApiErrorCode.AI_TRANSLATION_INVALID_RESPONSE;
        };
    }

    private List<String> orEmpty(List<String> values) {
        return values == null ? List.of() : values;
    }

    private boolean hasTimeoutCause(Throwable throwable) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (cause instanceof TimeoutException) {
                return true;
            }
        }
        return false;
    }
}
