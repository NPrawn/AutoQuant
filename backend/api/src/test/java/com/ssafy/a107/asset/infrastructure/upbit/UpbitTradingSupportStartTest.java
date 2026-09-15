package com.ssafy.a107.asset.infrastructure.upbit;

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
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/* 상장일 역추적은 페이지를 이어 받으므로 응답을 차례로 돌려주는 서버가 필요하다. */
class UpbitTradingSupportStartTest {

    /* ArrayDeque 는 null 을 담지 못해 서버 오류를 표시할 값을 따로 둔다. */
    private static final String SERVER_ERROR = "__error__";

    private final Deque<String> responses = new ArrayDeque<>();
    private final List<String> requestedQueries = new ArrayList<>();
    private HttpServer httpServer;

    @AfterEach
    void tearDown() {
        if (httpServer != null) {
            httpServer.stop(0);
        }
    }

    @DisplayName("일봉을 과거로 따라가 더 받을 것이 없으면 마지막 봉을 거래지원 시작일로 본다")
    @Test
    void findOldestCandleDate() throws IOException {
        responses.add(candles("2020-03-10T00:00:00", "2020-03-09T00:00:00"));
        responses.add(candles("2020-03-08T00:00:00", "2020-03-07T00:00:00"));
        responses.add("[]");
        UpbitMarketWebClient client = createClient();

        Optional<Instant> startedAt = client.findTradingSupportStartedAt("KRW-BTC");

        assertThat(startedAt).contains(Instant.parse("2020-03-07T00:00:00Z"));
        assertThat(requestedQueries).hasSize(3);
        /* 첫 요청에는 to 가 없고, 다음 요청부터 직전 페이지의 마지막 봉을 to 로 보낸다. */
        assertThat(requestedQueries.get(0)).doesNotContain("to=");
        assertThat(requestedQueries.get(1)).contains("to=2020-03-09T00:00:00");
        assertThat(requestedQueries.get(2)).contains("to=2020-03-07T00:00:00");
    }

    @DisplayName("첫 조회가 비어 있으면 거래지원 시작일을 비운다")
    @Test
    void emptyWhenNoCandle() throws IOException {
        responses.add("[]");

        assertThat(createClient().findTradingSupportStartedAt("KRW-NONE")).isEmpty();
    }

    @DisplayName("조회가 실패해도 등록을 막지 않고 그때까지 찾은 값을 돌려준다")
    @Test
    void keepFoundValueWhenRequestFails() throws IOException {
        responses.add(candles("2020-03-10T00:00:00", "2020-03-09T00:00:00"));
        responses.add(SERVER_ERROR);

        assertThat(createClient().findTradingSupportStartedAt("KRW-BTC"))
                .contains(Instant.parse("2020-03-09T00:00:00Z"));
    }

    private String candles(String... dateTimes) {
        StringBuilder body = new StringBuilder("[");
        for (int index = 0; index < dateTimes.length; index++) {
            if (index > 0) {
                body.append(',');
            }
            body.append("{\"candle_date_time_utc\":\"").append(dateTimes[index]).append("\"}");
        }
        return body.append(']').toString();
    }

    private UpbitMarketWebClient createClient() throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        httpServer.createContext("/v1/candles/days", this::respond);
        httpServer.start();

        String baseUrl = "http://127.0.0.1:" + httpServer.getAddress().getPort();
        return new UpbitMarketWebClient(
                WebClient.create(baseUrl),
                new UpbitProperties(baseUrl, Duration.ofSeconds(5))
        );
    }

    private void respond(HttpExchange exchange) throws IOException {
        requestedQueries.add(exchange.getRequestURI().getQuery());
        String next = responses.poll();
        /* 클라이언트가 조회 실패 뒤에도 그때까지 찾은 값을 지키는지 본다. */
        if (next == null || SERVER_ERROR.equals(next)) {
            exchange.sendResponseHeaders(HttpStatus.INTERNAL_SERVER_ERROR.value(), -1);
            exchange.close();
            return;
        }

        byte[] body = next.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        exchange.sendResponseHeaders(HttpStatus.OK.value(), body.length);
        try (var response = exchange.getResponseBody()) {
            response.write(body);
        }
    }
}
