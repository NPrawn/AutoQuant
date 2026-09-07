package com.ssafy.a107.ai.infrastructure;

import com.ssafy.a107.ai.service.port.AiStrategyTranslation;
import com.ssafy.a107.common.exception.ApiErrorCode;
import com.ssafy.a107.common.exception.ApiException;
import com.ssafy.a107.common.logging.RequestIdFilter;
import com.ssafy.a107.strategy.domain.dsl.ComparisonOperator;
import com.ssafy.a107.strategy.domain.dsl.ConditionFilterNode;
import com.ssafy.a107.strategy.domain.dsl.GroupFilterNode;
import com.ssafy.a107.strategy.domain.dsl.Timeframe;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.slf4j.MDC;
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

class AiStrategyTranslateWebClientTest {

    private static final String TRANSLATED_BODY = """
            {
              "strategy": {
                "timeframe": "1D",
                "filters": {
                  "type": "GROUP",
                  "logic": "AND",
                  "children": [
                    {
                      "type": "CONDITION",
                      "left": {"type": "FACTOR", "factor": "RETURN", "period": 20},
                      "operator": "GTE",
                      "right": {"type": "VALUE", "value": 5}
                    }
                  ]
                },
                "ranking": {"factor": "RETURN", "period": 20, "direction": "DESC"},
                "selection": {"topN": 5},
                "rebalance": {"period": "WEEKLY"}
              },
              "meta": {
                "attempts": 1,
                "model": "gpt-5.4-mini",
                "elapsedMs": 1984,
                "warnings": [],
                "assumptions": ["기본 시간프레임 1D를 사용했습니다."],
                "changes": []
              }
            }
            """;

    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private final AtomicReference<String> requestIdHeader = new AtomicReference<>();
    private HttpServer httpServer;

    @AfterEach
    void tearDown() {
        if (httpServer != null) {
            httpServer.stop(0);
        }
        MDC.clear();
    }

    @DisplayName("AI 서버가 돌려준 전략과 메타를 그대로 옮긴다")
    @Test
    void translateUserInput() throws IOException {
        AiStrategyTranslateWebClient client = createClient(HttpStatus.OK, TRANSLATED_BODY);

        AiStrategyTranslation translation = client.translate("5% 이상 오른 코인 사줘", null);

        assertThat(translation.strategy().timeframe()).isEqualTo(Timeframe.ONE_DAY);
        assertThat(translation.attempts()).isEqualTo(1);
        assertThat(translation.elapsedMs()).isEqualTo(1984L);
        assertThat(translation.assumptions()).containsExactly("기본 시간프레임 1D를 사용했습니다.");
        assertThat(requestBody.get()).contains("5% 이상 오른 코인 사줘");

        GroupFilterNode filters = (GroupFilterNode) translation.strategy().filters();
        ConditionFilterNode condition = (ConditionFilterNode) filters.children().getFirst();
        assertThat(condition.operator()).isEqualTo(ComparisonOperator.GTE);
    }

    @DisplayName("전략 없이 assumptions만 돌아와도 정상 응답으로 다룬다")
    @Test
    void acceptResponseWithoutStrategy() throws IOException {
        AiStrategyTranslateWebClient client = createClient(HttpStatus.OK, """
                {
                  "strategy": null,
                  "meta": {
                    "attempts": 0,
                    "model": "gpt-5.4-mini",
                    "elapsedMs": 1,
                    "warnings": [],
                    "assumptions": ["입력이 짧아 전략을 만들지 않았습니다."],
                    "changes": []
                  }
                }
                """);

        AiStrategyTranslation translation = client.translate("안녕하세요", null);

        assertThat(translation.strategy()).isNull();
        assertThat(translation.attempts()).isZero();
        assertThat(translation.assumptions()).hasSize(1);
    }

    @DisplayName("요청 식별자를 X-Request-ID 헤더로 AI 서버에 넘긴다")
    @Test
    void sendRequestIdHeader() throws IOException {
        AiStrategyTranslateWebClient client = createClient(HttpStatus.OK, TRANSLATED_BODY);
        MDC.put(RequestIdFilter.MDC_KEY, "req-1234");

        client.translate("5% 이상 오른 코인 사줘", null);

        assertThat(requestIdHeader.get()).isEqualTo("req-1234");
    }

    @DisplayName("요청 식별자가 없으면 헤더를 붙이지 않고 AI 서버가 만들게 둔다")
    @Test
    void omitRequestIdHeaderOutsideARequest() throws IOException {
        AiStrategyTranslateWebClient client = createClient(HttpStatus.OK, TRANSLATED_BODY);

        client.translate("5% 이상 오른 코인 사줘", null);

        assertThat(requestIdHeader.get()).isNull();
    }

    @DisplayName("검증에 실패한 422는 전략 변환 실패로 옮긴다")
    @Test
    void mapValidationFailure() throws IOException {
        AiStrategyTranslateWebClient client = createClient(HttpStatus.UNPROCESSABLE_ENTITY, """
                {"code": "DSL_VALIDATION_FAILED", "message": "validation failed", "detail": {"attempts": 3}}
                """);

        assertThatThrownBy(() -> client.translate("5% 이상 오른 코인 사줘", null))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getErrorCode())
                .isEqualTo(ApiErrorCode.AI_TRANSLATION_FAILED);
    }

    @DisplayName("AI 서버의 504는 타임아웃으로 옮긴다")
    @Test
    void mapUpstreamTimeout() throws IOException {
        AiStrategyTranslateWebClient client = createClient(HttpStatus.GATEWAY_TIMEOUT, """
                {"code": "LLM_TIMEOUT", "message": "timed out", "detail": null}
                """);

        assertThatThrownBy(() -> client.translate("5% 이상 오른 코인 사줘", null))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getErrorCode())
                .isEqualTo(ApiErrorCode.AI_TRANSLATION_TIMEOUT);
    }

    @DisplayName("AI 서버의 429는 변환 호출 한도 초과로 옮긴다")
    @Test
    void mapCreditLimit() throws IOException {
        AiStrategyTranslateWebClient client = createClient(HttpStatus.TOO_MANY_REQUESTS, """
                {"code": "LLM_CREDIT_LIMIT_EXCEEDED", "message": "too many calls", "detail": null}
                """);

        assertThatThrownBy(() -> client.translate("5% 이상 오른 코인 사줘", null))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getErrorCode())
                .isEqualTo(ApiErrorCode.AI_TRANSLATION_QUOTA_EXCEEDED);
    }

    @DisplayName("meta가 없는 응답은 처리할 수 없는 응답으로 다룬다")
    @Test
    void rejectResponseWithoutMeta() throws IOException {
        AiStrategyTranslateWebClient client = createClient(HttpStatus.OK, "{\"strategy\": null}");

        assertThatThrownBy(() -> client.translate("5% 이상 오른 코인 사줘", null))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getErrorCode())
                .isEqualTo(ApiErrorCode.AI_TRANSLATION_INVALID_RESPONSE);
    }

    @DisplayName("응답이 타임아웃보다 늦으면 타임아웃으로 다룬다")
    @Test
    void mapLocalTimeout() throws IOException {
        AiStrategyTranslateWebClient client = createClient(
                HttpStatus.OK,
                TRANSLATED_BODY,
                Duration.ofMillis(100),
                Duration.ofMillis(400)
        );

        assertThatThrownBy(() -> client.translate("5% 이상 오른 코인 사줘", null))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getErrorCode())
                .isEqualTo(ApiErrorCode.AI_TRANSLATION_TIMEOUT);
    }

    @DisplayName("AI 서버에 닿지 못하면 일시적 사용 불가로 다룬다")
    @Test
    void mapConnectionFailure() {
        AiProperties properties = new AiProperties("http://localhost:1", Duration.ofSeconds(1), 10);
        AiStrategyTranslateWebClient client =
                new AiStrategyTranslateWebClient(WebClient.create(properties.baseUrl()), properties);

        assertThatThrownBy(() -> client.translate("5% 이상 오른 코인 사줘", null))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getErrorCode())
                .isEqualTo(ApiErrorCode.AI_TRANSLATION_UNAVAILABLE);
    }

    private AiStrategyTranslateWebClient createClient(HttpStatus status, String body) throws IOException {
        return createClient(status, body, Duration.ofSeconds(5), Duration.ZERO);
    }

    private AiStrategyTranslateWebClient createClient(
            HttpStatus status,
            String body,
            Duration timeout,
            Duration serverDelay
    ) throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        httpServer.createContext("/api/v1/ai/strategies/translate", exchange -> respond(exchange, status, body, serverDelay));
        httpServer.start();

        AiProperties properties = new AiProperties(
                "http://localhost:" + httpServer.getAddress().getPort(),
                timeout,
                10
        );
        return new AiStrategyTranslateWebClient(WebClient.create(properties.baseUrl()), properties);
    }

    private void respond(HttpExchange exchange, HttpStatus status, String body, Duration serverDelay)
            throws IOException {
        requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        requestIdHeader.set(exchange.getRequestHeaders().getFirst(RequestIdFilter.HEADER_NAME));
        if (!serverDelay.isZero()) {
            try {
                Thread.sleep(serverDelay.toMillis());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }

        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        exchange.sendResponseHeaders(status.value(), payload.length);
        exchange.getResponseBody().write(payload);
        exchange.close();
    }
}
