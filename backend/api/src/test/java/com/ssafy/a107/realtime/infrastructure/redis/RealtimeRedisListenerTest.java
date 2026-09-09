package com.ssafy.a107.realtime.infrastructure.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ssafy.a107.realtime.RealtimeFixtures;
import com.ssafy.a107.realtime.domain.LiveMinuteCandle;
import com.ssafy.a107.realtime.domain.RealtimeTrade;
import com.ssafy.a107.realtime.service.RealtimeEventPublisher;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.redis.connection.DefaultMessage;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class RealtimeRedisListenerTest {

    private static final ValidatorFactory VALIDATORS = Validation.buildDefaultValidatorFactory();
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final List<Object> events = new ArrayList<>();
    private final RealtimeRedisListener listener = new RealtimeRedisListener(
            MAPPER, VALIDATORS.getValidator(), new RealtimeEventPublisher() {
                @Override
                public void publish(RealtimeTrade trade) {
                    events.add(trade);
                }

                @Override
                public void publish(LiveMinuteCandle candle) {
                    events.add(candle);
                }
            }
    );

    @AfterAll
    static void closeValidatorFactory() {
        VALIDATORS.close();
    }

    @Test
    @DisplayName("체결 메시지의 식별자와 정밀도를 보존하여 변환한다")
    void convertTrade() {
        receive(RealtimeRedisChannels.TRADE, RealtimeFixtures.TRADE);

        assertThat(events).containsExactly(new RealtimeTrade(
                UUID.fromString("11111111-1111-1111-1111-111111111111"), "KRW-BTC",
                "100000000.12345678", "0.00000001", "BID", 1800000000123L, 123456789L
        ));
    }

    @Test
    @DisplayName("진행 봉의 모든 필드를 인메모리 이벤트로 변환한다")
    void convertCandle() {
        receive(RealtimeRedisChannels.LIVE_MINUTE_CANDLE, RealtimeFixtures.CANDLE);

        assertThat(events).containsExactly(new LiveMinuteCandle(
                UUID.fromString("11111111-1111-1111-1111-111111111111"), "KRW-BTC", 1,
                1800000000000L, "100000000.12345678", "100000001", "99999999", "100000000",
                "0.00000001", "1.0000000012345678", 1800000000123L, 123456789L
        ));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 5, 15, 60, 240, 1440})
    @DisplayName("지원하는 간격의 채널에서 UTC 경계에 맞는 봉을 수신한다")
    void receiveSupportedIntervals(int interval) throws Exception {
        ObjectNode payload = (ObjectNode) MAPPER.readTree(RealtimeFixtures.CANDLE);
        long width = interval * 60_000L;
        long start = 1800000000000L / width * width;
        payload.put("interval_minutes", interval);
        payload.put("open_time_unix_milli", start);
        payload.put("last_event_time_unix_milli", start + 123);

        receive(RealtimeRedisChannels.candle(interval), payload.toString());

        assertThat(events).singleElement().isInstanceOfSatisfying(LiveMinuteCandle.class,
                candle -> assertThat(candle.intervalMinutes()).isEqualTo(interval));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{broken", "null", "[]", "{}", "{} {}"})
    @DisplayName("손상되거나 불완전한 JSON은 버리고 다음 메시지를 처리한다")
    void discardMalformedJson(String payload) {
        receive(RealtimeRedisChannels.TRADE, payload);
        receive(RealtimeRedisChannels.TRADE, RealtimeFixtures.TRADE);

        assertThat(events).hasSize(1);
    }

    @ParameterizedTest
    @MethodSource("messagesWithMissingFields")
    @DisplayName("두 채널의 모든 필수 필드 누락을 거부한다")
    void discardMissingFields(String channel, String payload) {
        receive(channel, payload);

        assertThat(events).isEmpty();
    }

    static Stream<Object[]> messagesWithMissingFields() throws Exception {
        List<Object[]> messages = new ArrayList<>();
        for (String[] sample : List.of(
                new String[]{RealtimeRedisChannels.TRADE, RealtimeFixtures.TRADE},
                new String[]{RealtimeRedisChannels.LIVE_MINUTE_CANDLE, RealtimeFixtures.CANDLE}
        )) {
            ObjectNode original = (ObjectNode) MAPPER.readTree(sample[1]);
            original.fieldNames().forEachRemaining(field -> {
                ObjectNode missing = original.deepCopy();
                missing.remove(field);
                messages.add(new Object[]{sample[0], missing.toString()});
                ObjectNode nullValue = original.deepCopy();
                nullValue.putNull(field);
                messages.add(new Object[]{sample[0], nullValue.toString()});
            });
        }
        return messages.stream();
    }

    @ParameterizedTest
    @MethodSource("invalidFields")
    @DisplayName("종목 코드와 식별자 및 값이 계약을 벗어나면 중계하지 않는다")
    void discardInvalidFields(String channel, String fixture, String field, String value) throws Exception {
        ObjectNode payload = (ObjectNode) MAPPER.readTree(fixture);
        payload.put(field, value);
        receive(channel, payload.toString());

        assertThat(events).isEmpty();
    }

    static Stream<Object[]> invalidFields() {
        List<Object[]> messages = new ArrayList<>();
        for (String[] sample : List.of(
                new String[]{RealtimeRedisChannels.TRADE, RealtimeFixtures.TRADE},
                new String[]{RealtimeRedisChannels.LIVE_MINUTE_CANDLE, RealtimeFixtures.CANDLE}
        )) {
            for (String[] field : List.of(
                    new String[]{"asset_id", "not-a-uuid"}, new String[]{"asset_id", "1-1-1-1-1"},
                    new String[]{"market_code", "KRW-*"}, new String[]{"market_code", "KRW-BTC/trades"},
                    new String[]{"market_code", "krw-btc"}, new String[]{"market_code", "BTC-ETH"}
            )) {
                messages.add(new Object[]{sample[0], sample[1], field[0], field[1]});
            }
        }
        for (String[] field : List.of(
                new String[]{"side", "BUY"}, new String[]{"price", "NaN"}, new String[]{"price", "0"},
                new String[]{"volume", "-1"}, new String[]{"traded_at_unix_milli", "0"},
                new String[]{"sequence_id", "-1"}
        )) {
            messages.add(new Object[]{RealtimeRedisChannels.TRADE, RealtimeFixtures.TRADE, field[0], field[1]});
        }
        for (String[] field : List.of(
                new String[]{"interval_minutes", "5"}, new String[]{"open_time_unix_milli", "-1"},
                new String[]{"last_event_time_unix_milli", "0"}, new String[]{"last_sequence_id", "-1"},
                new String[]{"close_price", "bad"}, new String[]{"trade_volume", "-1"}
        )) {
            messages.add(new Object[]{RealtimeRedisChannels.LIVE_MINUTE_CANDLE,
                    RealtimeFixtures.CANDLE, field[0], field[1]});
        }
        return messages.stream();
    }

    @Test
    @DisplayName("발행 실패가 이후 메시지 수신을 중단시키지 않는다")
    void continueAfterPublisherFailure() {
        List<RealtimeTrade> delivered = new ArrayList<>();
        RealtimeRedisListener failingListener = new RealtimeRedisListener(
                MAPPER, VALIDATORS.getValidator(), new RealtimeEventPublisher() {
                    private boolean fail = true;

                    @Override
                    public void publish(RealtimeTrade trade) {
                        if (fail) {
                            fail = false;
                            throw new IllegalStateException("broker unavailable");
                        }
                        delivered.add(trade);
                    }

                    @Override
                    public void publish(LiveMinuteCandle candle) {
                    }
                }
        );
        DefaultMessage message = message(RealtimeRedisChannels.TRADE, RealtimeFixtures.TRADE);
        failingListener.onMessage(message, null);
        failingListener.onMessage(message, null);

        assertThat(delivered).hasSize(1);
    }

    private void receive(String channel, String payload) {
        listener.onMessage(message(channel, payload), null);
    }

    private static DefaultMessage message(String channel, String payload) {
        return new DefaultMessage(channel.getBytes(StandardCharsets.UTF_8), payload.getBytes(StandardCharsets.UTF_8));
    }
}
