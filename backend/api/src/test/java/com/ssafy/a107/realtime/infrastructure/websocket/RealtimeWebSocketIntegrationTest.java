package com.ssafy.a107.realtime.infrastructure.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.a107.auth.config.SecurityConfig;
import com.ssafy.a107.auth.jwt.ApiAuthenticationEntryPoint;
import com.ssafy.a107.auth.jwt.AuthenticationErrorWriter;
import com.ssafy.a107.auth.jwt.JwtAuthenticationFilter;
import com.ssafy.a107.auth.jwt.JwtTokenProvider;
import com.ssafy.a107.realtime.RealtimeFixtures;
import com.ssafy.a107.realtime.infrastructure.redis.RealtimeRedisListener;
import com.ssafy.a107.realtime.infrastructure.redis.RealtimeRedisConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.messaging.MessageHeaders;

import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(
        classes = RealtimeWebSocketIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "jwt.secret=realtime-test-only-signing-key-at-least-32-bytes",
                "jwt.issuer=realtime-test",
                "jwt.access-token-validity=1h",
                "auth.frontend-base-url=http://localhost:5173"
        }
)
class RealtimeWebSocketIntegrationTest {

    private final int port;
    private final JwtTokenProvider tokens;
    private final RealtimeRedisListener listener;
    private final SimpleBrokerMessageHandler broker;
    private final ObjectMapper objectMapper;
    private final List<WebSocketStompClient> clients = new ArrayList<>();
    private final List<StompSession> sessions = new ArrayList<>();

    @Autowired
    RealtimeWebSocketIntegrationTest(
            @LocalServerPort int port, JwtTokenProvider tokens, RealtimeRedisListener listener,
            SimpleBrokerMessageHandler broker, ObjectMapper objectMapper
    ) {
        this.port = port;
        this.tokens = tokens;
        this.listener = listener;
        this.broker = broker;
        this.objectMapper = objectMapper;
    }

    @AfterEach
    void closeConnections() {
        sessions.stream().filter(StompSession::isConnected).forEach(StompSession::disconnect);
        sessions.clear();
        clients.forEach(WebSocketStompClient::stop);
        clients.clear();
        await().atMost(Duration.ofSeconds(5)).until(() -> subscriptionCount("/topic/markets/KRW-BTC/trades") == 0);
    }

    @Test
    @DisplayName("JWT 쿠키로 연결한 하나의 소켓에서 여러 종목을 구독하고 선택적으로 해제한다")
    void relayMultipleMarketsAndUnsubscribe() throws Exception {
        StompSession session = connect(new BlockingQueueSessionHandler());
        BlockingQueue<String> bitcoin = new LinkedBlockingQueue<>();
        BlockingQueue<String> ethereum = new LinkedBlockingQueue<>();
        StompSession.Subscription bitcoinSubscription = session.subscribe(
                "/topic/markets/KRW-BTC/trades", frames(bitcoin));
        session.subscribe("/topic/markets/KRW-ETH/trades", frames(ethereum));
        awaitSubscriptions("/topic/markets/KRW-BTC/trades", 1);
        awaitSubscriptions("/topic/markets/KRW-ETH/trades", 1);

        receive("trade:realtime:updates", RealtimeFixtures.TRADE);
        receive("trade:realtime:updates", RealtimeFixtures.TRADE.replace("KRW-BTC", "KRW-ETH"));

        JsonNode bitcoinTrade = objectMapper.readTree(take(bitcoin));
        JsonNode ethereumTrade = objectMapper.readTree(take(ethereum));
        assertThat(bitcoinTrade.path("marketCode").asText()).isEqualTo("KRW-BTC");
        assertThat(ethereumTrade.path("marketCode").asText()).isEqualTo("KRW-ETH");
        assertThat(bitcoinTrade.path("price").isTextual()).isTrue();
        assertThat(bitcoinTrade.path("price").asText()).isEqualTo("100000000.12345678");
        assertThat(bitcoinTrade.path("assetId").asText()).isEqualTo("11111111-1111-1111-1111-111111111111");
        assertThat(bitcoinTrade.path("tradedAtUnixMilli").asLong()).isEqualTo(1800000000123L);
        assertThat(bitcoinTrade.has("market_code")).isFalse();
        assertThat(bitcoinTrade.size()).isEqualTo(7);

        bitcoinSubscription.unsubscribe();
        awaitSubscriptions("/topic/markets/KRW-BTC/trades", 0);
        receive("trade:realtime:updates", RealtimeFixtures.TRADE);
        receive("trade:realtime:updates", RealtimeFixtures.TRADE.replace("KRW-BTC", "KRW-ETH"));

        assertThat(take(ethereum)).contains("KRW-ETH");
        assertThat(bitcoin.poll(200, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    @DisplayName("진행 봉의 전체 스키마를 문자열 정밀도와 함께 전달한다")
    void relayLiveCandle() throws Exception {
        StompSession session = connect(new BlockingQueueSessionHandler());
        BlockingQueue<String> candles = new LinkedBlockingQueue<>();
        session.subscribe("/topic/markets/KRW-BTC/candles/1m", frames(candles));
        awaitSubscriptions("/topic/markets/KRW-BTC/candles/1m", 1);

        receive("candle:1m:live:updates", RealtimeFixtures.CANDLE);

        JsonNode candle = objectMapper.readTree(take(candles));
        assertThat(candle.size()).isEqualTo(12);
        assertThat(candle.path("marketCode").asText()).isEqualTo("KRW-BTC");
        assertThat(candle.path("intervalMinutes").asInt()).isEqualTo(1);
        assertThat(candle.path("openTimeUnixMilli").asLong()).isEqualTo(1800000000000L);
        assertThat(candle.path("openPrice").asText()).isEqualTo("100000000.12345678");
        assertThat(candle.path("tradeAmount").asText()).isEqualTo("1.0000000012345678");
        assertThat(candle.path("lastSequenceId").asLong()).isEqualTo(123456789L);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "REALTIME_TEST_REDIS_PORT", matches = "[0-9]+")
    @DisplayName("실제 Redis 발행이 종목별 WebSocket 메시지까지 도달한다")
    void relayFromRealRedisToWebSocket() throws Exception {
        StompSession session = connect(new BlockingQueueSessionHandler());
        BlockingQueue<String> trades = new LinkedBlockingQueue<>();
        session.subscribe("/topic/markets/KRW-BTC/trades", frames(trades));
        awaitSubscriptions("/topic/markets/KRW-BTC/trades", 1);
        LettuceConnectionFactory connection = new LettuceConnectionFactory(
                "127.0.0.1", Integer.parseInt(System.getenv("REALTIME_TEST_REDIS_PORT")));
        RealtimeRedisConfig config = new RealtimeRedisConfig();
        ThreadPoolTaskExecutor executor = config.realtimeRedisExecutor();
        RedisMessageListenerContainer container = config.realtimeRedisContainer(connection, listener, executor);
        try {
            connection.afterPropertiesSet();
            executor.initialize();
            container.afterPropertiesSet();
            container.start();
            StringRedisTemplate redis = new StringRedisTemplate(connection);

            assertThat(redis.convertAndSend("trade:realtime:updates", RealtimeFixtures.TRADE)).isEqualTo(1);

            assertThat(objectMapper.readTree(take(trades)).path("marketCode").asText()).isEqualTo("KRW-BTC");
        } finally {
            container.destroy();
            executor.shutdown();
            connection.destroy();
        }
    }

    @Test
    @DisplayName("연속된 체결은 수신 순서대로 같은 종목 구독자에게 전달된다")
    void preserveTradeOrder() throws Exception {
        StompSession session = connect(new BlockingQueueSessionHandler());
        BlockingQueue<String> trades = new LinkedBlockingQueue<>();
        session.subscribe("/topic/markets/KRW-BTC/trades", frames(trades));
        awaitSubscriptions("/topic/markets/KRW-BTC/trades", 1);

        for (int sequence = 1; sequence <= 20; sequence++) {
            receive("trade:realtime:updates", RealtimeFixtures.TRADE.replace("123456789", String.valueOf(sequence)));
        }

        for (int sequence = 1; sequence <= 20; sequence++) {
            assertThat(objectMapper.readTree(take(trades)).path("sequenceId").asLong()).isEqualTo(sequence);
        }
    }

    @Test
    @DisplayName("서로 다른 연결은 각자 구독한 종목만 받는다")
    void isolateClientSubscriptions() throws Exception {
        StompSession first = connect(new BlockingQueueSessionHandler());
        StompSession second = connect(new BlockingQueueSessionHandler());
        BlockingQueue<String> bitcoin = new LinkedBlockingQueue<>();
        BlockingQueue<String> ethereum = new LinkedBlockingQueue<>();
        first.subscribe("/topic/markets/KRW-BTC/trades", frames(bitcoin));
        second.subscribe("/topic/markets/KRW-ETH/trades", frames(ethereum));
        awaitSubscriptions("/topic/markets/KRW-BTC/trades", 1);
        awaitSubscriptions("/topic/markets/KRW-ETH/trades", 1);

        receive("trade:realtime:updates", RealtimeFixtures.TRADE);

        assertThat(take(bitcoin)).contains("KRW-BTC");
        assertThat(ethereum.poll(200, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    @DisplayName("인증 없는 WebSocket 업그레이드 요청은 401로 거부한다")
    void rejectUnauthenticatedHandshake() throws Exception {
        HttpResponse<?> response = handshake(null, null);

        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("만료된 JWT 쿠키로 WebSocket 연결을 만들 수 없다")
    void rejectExpiredCookie() throws Exception {
        String expired = tokens.issue(UUID.randomUUID(), Instant.now().minusSeconds(7200)).value();
        HttpResponse<?> response = handshake(expired, null);

        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("유효한 JWT가 있어도 다른 오리진의 WebSocket 연결은 거부한다")
    void rejectCrossOrigin() throws Exception {
        HttpResponse<?> response = handshake(validToken(), "https://untrusted.example");

        assertThat(response.statusCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("클라이언트가 가짜 시세를 발행하면 오류를 보내고 연결을 종료한다")
    void rejectClientPublishing() throws Exception {
        BlockingQueueSessionHandler handler = new BlockingQueueSessionHandler();
        StompSession session = connect(handler);
        BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        session.subscribe("/topic/markets/KRW-BTC/trades", frames(messages));
        awaitSubscriptions("/topic/markets/KRW-BTC/trades", 1);

        session.send("/topic/markets/KRW-BTC/trades", "secret-forged-payload");

        String error = take(handler.errors);
        assertThat(error).contains("허용되지 않은").doesNotContain("secret-forged-payload");
        await().atMost(Duration.ofSeconds(5)).until(() -> !session.isConnected());
        assertThat(messages.poll(200, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    @DisplayName("와일드카드 구독을 실제 STOMP 경로에서 거부한다")
    void rejectWildcardSubscription() throws Exception {
        BlockingQueueSessionHandler handler = new BlockingQueueSessionHandler();
        StompSession session = connect(handler);

        session.subscribe("/topic/markets/*/trades", frames(new LinkedBlockingQueue<>()));

        assertThat(take(handler.errors)).contains("허용되지 않은");
        await().atMost(Duration.ofSeconds(5)).until(() -> !session.isConnected());
    }

    private StompSession connect(BlockingQueueSessionHandler handler) throws Exception {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new StringMessageConverter() {
            @Override
            protected boolean supportsMimeType(MessageHeaders messageHeaders) {
                return true;
            }
        });
        clients.add(client);
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.add("Cookie", "access_token=" + validToken());
        StompSession session = client.connectAsync("ws://localhost:" + port + "/ws", headers, handler)
                .get(5, TimeUnit.SECONDS);
        sessions.add(session);
        return session;
    }

    private String validToken() {
        return tokens.issue(UUID.randomUUID(), Instant.now()).value();
    }

    private HttpResponse<?> handshake(String token, String origin) {
        try (HttpClient client = HttpClient.newHttpClient()) {
            WebSocket.Builder request = client.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(5));
            if (token != null) {
                request.header("Cookie", "access_token=" + token);
            }
            if (origin != null) {
                request.header("Origin", origin);
            }
            try {
                WebSocket connection = request.buildAsync(URI.create("ws://localhost:" + port + "/ws"),
                        new WebSocket.Listener() { }).join();
                connection.abort();
                throw new AssertionError("Handshake should be rejected");
            } catch (CompletionException exception) {
                assertThat(exception.getCause()).isInstanceOf(WebSocketHandshakeException.class);
                return ((WebSocketHandshakeException) exception.getCause()).getResponse();
            }
        }
    }

    private void receive(String channel, String payload) {
        listener.onMessage(new DefaultMessage(channel.getBytes(StandardCharsets.UTF_8),
                payload.getBytes(StandardCharsets.UTF_8)), null);
    }

    private void awaitSubscriptions(String destination, int expected) {
        await().atMost(Duration.ofSeconds(5)).until(() -> subscriptionCount(destination) == expected);
    }

    private int subscriptionCount(String destination) {
        SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        headers.setDestination(destination);
        return broker.getSubscriptionRegistry()
                .findSubscriptions(MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders()))
                .values().stream().mapToInt(List::size).sum();
    }

    private static StompFrameHandler frames(BlockingQueue<String> messages) {
        return new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return String.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                messages.add((String) payload);
            }
        };
    }

    private static String take(BlockingQueue<String> queue) throws InterruptedException {
        String message = queue.poll(5, TimeUnit.SECONDS);
        assertThat(message).as("WebSocket 프레임 수신").isNotNull();
        return message;
    }

    private static class BlockingQueueSessionHandler extends StompSessionHandlerAdapter {
        private final BlockingQueue<String> errors = new LinkedBlockingQueue<>();

        @Override
        public Type getPayloadType(StompHeaders headers) {
            return String.class;
        }

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            errors.add((String) payload);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class, FlywayAutoConfiguration.class,
            RedisAutoConfiguration.class, RedisRepositoriesAutoConfiguration.class
    })
    @Import({
            SecurityConfig.class, JwtTokenProvider.class, JwtAuthenticationFilter.class,
            AuthenticationErrorWriter.class, ApiAuthenticationEntryPoint.class,
            RealtimeWebSocketConfig.class, RealtimeSubscriptionInterceptor.class, RealtimeStompErrorHandler.class,
            StompRealtimeEventPublisher.class, RealtimeRedisListener.class
    })
    static class TestApplication {
    }
}
