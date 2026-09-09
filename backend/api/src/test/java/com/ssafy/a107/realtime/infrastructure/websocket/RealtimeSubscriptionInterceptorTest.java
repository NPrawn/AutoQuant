package com.ssafy.a107.realtime.infrastructure.websocket;

import com.ssafy.a107.common.exception.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RealtimeSubscriptionInterceptorTest {

    private final RealtimeSubscriptionInterceptor interceptor = new RealtimeSubscriptionInterceptor();

    @ParameterizedTest
    @ValueSource(strings = {"/topic/markets/KRW-BTC/trades", "/topic/markets/KRW-ETH/trades",
            "/topic/markets/KRW-BTC/candles/1m", "/topic/markets/KRW-NEWCOIN/candles/1m",
            "/topic/markets/KRW-BTC/candles/2m", "/topic/markets/KRW-BTC/candles/5m",
            "/topic/markets/KRW-BTC/candles/15m", "/topic/markets/KRW-BTC/candles/60m",
            "/topic/markets/KRW-BTC/candles/240m", "/topic/markets/KRW-BTC/candles/1440m"})
    @DisplayName("종목별 체결과 진행 봉 구독을 허용한다")
    void allowMarketSubscriptions(String destination) {
        Message<byte[]> message = message(StompCommand.SUBSCRIBE, destination);

        assertThat(interceptor.preSend(message, null)).isSameAs(message);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "/topic/**", "/topic/markets/*/trades", "/topic/markets/KRW-BTC/trades/",
            "/topic/markets/KRW-BTC/candles/3m", "/topic/markets/KRW-BTC", "/queue/private",
            "/topic/markets/{marketCode}/trades", "/topic/markets/KRW-BTC/trades?x=1"})
    @DisplayName("와일드카드와 정의되지 않은 구독 경로를 거부한다")
    void rejectInvalidSubscriptions(String destination) {
        assertThatThrownBy(() -> interceptor.preSend(message(StompCommand.SUBSCRIBE, destination), null))
                .isInstanceOf(ApiException.class);
    }

    @ParameterizedTest
    @EnumSource(value = StompCommand.class, names = {"SEND", "BEGIN", "ACK", "NACK", "COMMIT", "ABORT"})
    @DisplayName("클라이언트의 시세 발행과 지원하지 않는 명령을 거부한다")
    void rejectUnsupportedCommands(StompCommand command) {
        assertThatThrownBy(() -> interceptor.preSend(message(command, "/topic/markets/KRW-BTC/trades"), null))
                .isInstanceOf(ApiException.class);
    }

    private static Message<byte[]> message(StompCommand command, String destination) {
        StompHeaderAccessor headers = StompHeaderAccessor.create(command);
        headers.setUser(new UsernamePasswordAuthenticationToken("member", null, List.of()));
        headers.setDestination(destination);
        return MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
    }
}
