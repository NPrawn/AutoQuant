package com.ssafy.a107.realtime.infrastructure.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.a107.realtime.RealtimeFixtures;
import com.ssafy.a107.realtime.domain.LiveMinuteCandle;
import com.ssafy.a107.realtime.domain.RealtimeTrade;
import com.ssafy.a107.realtime.service.RealtimeEventPublisher;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@EnabledIfEnvironmentVariable(named = "REALTIME_TEST_REDIS_PORT", matches = "[0-9]+")
class RealtimeRedisIntegrationTest {

    @Test
    @DisplayName("각 API 인스턴스는 두 Redis 채널을 한 번씩 구독하고 동일한 갱신을 받는다")
    void subscribeOncePerChannelAndInstance() {
        RecordingPublisher first = new RecordingPublisher();
        RecordingPublisher second = new RecordingPublisher();
        try (ValidatorFactory validators = Validation.buildDefaultValidatorFactory();
             AnnotationConfigApplicationContext firstInstance = instance(first, validators.getValidator());
             AnnotationConfigApplicationContext secondInstance = instance(second, validators.getValidator())) {
            StringRedisTemplate redis = new StringRedisTemplate(firstInstance.getBean(LettuceConnectionFactory.class));

            assertThat(redis.convertAndSend(RealtimeRedisChannels.TRADE, RealtimeFixtures.TRADE)).isEqualTo(2);
            assertThat(redis.convertAndSend(RealtimeRedisChannels.LIVE_MINUTE_CANDLE, RealtimeFixtures.CANDLE)).isEqualTo(2);
            redis.convertAndSend(RealtimeRedisChannels.TRADE, "{broken");
            redis.convertAndSend(RealtimeRedisChannels.TRADE, RealtimeFixtures.TRADE.replace("KRW-BTC", "KRW-ETH"));

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                assertThat(first.events).hasSize(3);
                assertThat(second.events).containsExactlyElementsOf(first.events);
            });
            assertThat(first.events.get(0)).isInstanceOf(RealtimeTrade.class);
            assertThat(first.events.get(1)).isInstanceOf(LiveMinuteCandle.class);
            assertThat((RealtimeTrade) first.events.get(2)).extracting(RealtimeTrade::marketCode).isEqualTo("KRW-ETH");
        }
    }

    private AnnotationConfigApplicationContext instance(RecordingPublisher publisher, Validator validator) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.registerBean(LettuceConnectionFactory.class, () -> new LettuceConnectionFactory(
                "127.0.0.1", Integer.parseInt(System.getenv("REALTIME_TEST_REDIS_PORT"))));
        context.registerBean(ObjectMapper.class, () -> new ObjectMapper());
        context.registerBean(Validator.class, () -> validator);
        context.registerBean(RealtimeEventPublisher.class, () -> publisher);
        context.register(RealtimeRedisConfig.class, RealtimeRedisListener.class);
        context.refresh();
        return context;
    }

    private static class RecordingPublisher implements RealtimeEventPublisher {
        private final List<Object> events = new CopyOnWriteArrayList<>();

        @Override
        public void publish(RealtimeTrade trade) {
            events.add(trade);
        }

        @Override
        public void publish(LiveMinuteCandle candle) {
            events.add(candle);
        }
    }
}
