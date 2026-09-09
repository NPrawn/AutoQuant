package com.ssafy.a107.realtime.infrastructure.redis;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.ssafy.a107.realtime.service.RealtimeEventPublisher;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class RealtimeRedisListener implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(RealtimeRedisListener.class);

    private final ObjectReader tradeReader;
    private final ObjectReader candleReader;
    private final Validator validator;
    private final RealtimeEventPublisher publisher;

    public RealtimeRedisListener(ObjectMapper objectMapper, Validator validator, RealtimeEventPublisher publisher) {
        this.tradeReader = objectMapper.readerFor(RealtimeTradeMessage.class)
                .without(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        this.candleReader = objectMapper.readerFor(LiveMinuteCandleMessage.class)
                .without(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        this.validator = validator;
        this.publisher = publisher;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
        try {
            if (RealtimeRedisChannels.TRADE.equals(channel)) {
                RealtimeTradeMessage trade = readValid(tradeReader, message.getBody());
                publisher.publish(trade.toEvent());
            } else if (RealtimeRedisChannels.CANDLES.contains(channel)) {
                LiveMinuteCandleMessage candle = readValid(candleReader, message.getBody());
                long width = candle.intervalMinutes() * 60_000L;
                if (!channel.equals(RealtimeRedisChannels.candle(candle.intervalMinutes()))
                        || candle.openTimeUnixMilli() % width != 0
                        || candle.lastEventTimeUnixMilli() < candle.openTimeUnixMilli()
                        || candle.lastEventTimeUnixMilli() - candle.openTimeUnixMilli() >= width) {
                    throw new IllegalArgumentException("Invalid candle interval");
                }
                publisher.publish(candle.toEvent());
            } else {
                log.warn("Ignoring unknown realtime Redis channel");
            }
        } catch (IOException | IllegalArgumentException exception) {
            // 역직렬화 예외에는 원문이 포함될 수 있어 메시지와 스택을 기록하지 않는다.
            log.warn("Discarding invalid realtime Redis message");
        } catch (RuntimeException exception) {
            log.warn("Failed to relay realtime Redis message: {}", exception.getClass().getSimpleName());
        }
    }

    private <T> T readValid(ObjectReader reader, byte[] payload) throws IOException {
        T value = reader.readValue(payload);
        if (value == null || !validator.validate(value).isEmpty()) {
            throw new IllegalArgumentException("Invalid realtime Redis message");
        }
        return value;
    }
}
