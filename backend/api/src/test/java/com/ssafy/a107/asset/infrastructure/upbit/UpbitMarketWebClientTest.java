package com.ssafy.a107.asset.infrastructure.upbit;

import com.ssafy.a107.asset.service.port.UpbitMarketInformation;
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
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UpbitMarketWebClientTest {

    private final AtomicReference<String> requestedQuery = new AtomicReference<>();
    private HttpServer httpServer;

    @AfterEach
    void tearDown() {
        if (httpServer != null) {
            httpServer.stop(0);
        }
    }

    @DisplayName("Upbit 페어 목록에서 요청한 종목 정보를 찾는다")
    @Test
    void findRequestedMarket() throws IOException {
        WebClient webClient = createWebClient(HttpStatus.OK, """
                [
                  {
                    "market": "KRW-BTC",
                    "korean_name": "비트코인",
                    "english_name": "Bitcoin"
                  },
                  {
                    "market": "KRW-ETH",
                    "korean_name": "이더리움",
                    "english_name": "Ethereum"
                  }
                ]
                """);
        UpbitMarketWebClient client = createClient(webClient);

        Optional<UpbitMarketInformation> result = client.findMarket("KRW-BTC");

        assertThat(result).contains(new UpbitMarketInformation("KRW-BTC", "비트코인", "Bitcoin"));
        assertThat(requestedQuery.get()).isEqualTo("is_details=false");
    }

    @DisplayName("Upbit 응답에 요청한 종목이 없으면 빈 결과를 반환한다")
    @Test
    void returnEmptyWhenMarketDoesNotExist() throws IOException {
        WebClient webClient = createWebClient(HttpStatus.OK, "[]");
        UpbitMarketWebClient client = createClient(webClient);

        Optional<UpbitMarketInformation> result = client.findMarket("KRW-NONE");

        assertThat(result).isEmpty();
    }

    @DisplayName("Upbit 종목 응답의 필수 정보가 없으면 잘못된 외부 응답으로 처리한다")
    @Test
    void rejectInvalidMarketResponse() throws IOException {
        WebClient webClient = createWebClient(HttpStatus.OK, """
                [
                  {
                    "market": "KRW-BTC",
                    "korean_name": "비트코인"
                  }
                ]
                """);
        UpbitMarketWebClient client = createClient(webClient);

        assertThatThrownBy(() -> client.findMarket("KRW-BTC"))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ApiErrorCode.ASSET_EXCHANGE_INVALID_RESPONSE)
                );
    }

    @DisplayName("Upbit가 오류 상태를 반환하면 거래소 조회 장애로 처리한다")
    @Test
    void handleUpbitHttpError() throws IOException {
        WebClient webClient = createWebClient(HttpStatus.SERVICE_UNAVAILABLE, "{}");
        UpbitMarketWebClient client = createClient(webClient);

        assertThatThrownBy(() -> client.findMarket("KRW-BTC"))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ApiErrorCode.ASSET_EXCHANGE_UNAVAILABLE)
                );
    }

    private UpbitMarketWebClient createClient(WebClient webClient) {
        return new UpbitMarketWebClient(
                webClient,
                new UpbitProperties("https://api.upbit.com", Duration.ofSeconds(5))
        );
    }

    private WebClient createWebClient(HttpStatus status, String responseBody) throws IOException {
        httpServer = HttpServer.create(
                new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
                0
        );
        httpServer.createContext("/v1/market/all", exchange -> respond(exchange, status, responseBody));
        httpServer.start();

        String baseUrl = "http://127.0.0.1:" + httpServer.getAddress().getPort();
        return WebClient.create(baseUrl);
    }

    private void respond(HttpExchange exchange, HttpStatus status, String responseBody) throws IOException {
        requestedQuery.set(exchange.getRequestURI().getQuery());
        byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        exchange.sendResponseHeaders(status.value(), body.length);
        try (var response = exchange.getResponseBody()) {
            response.write(body);
        }
    }
}
