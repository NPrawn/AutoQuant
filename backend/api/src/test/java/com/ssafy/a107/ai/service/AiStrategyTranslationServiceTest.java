package com.ssafy.a107.ai.service;

import com.ssafy.a107.ai.infrastructure.AiProperties;
import com.ssafy.a107.ai.service.port.AiStrategyTranslation;
import com.ssafy.a107.ai.service.port.AiStrategyTranslator;
import com.ssafy.a107.common.exception.ApiErrorCode;
import com.ssafy.a107.common.exception.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiStrategyTranslationServiceTest {

    private static final UUID MEMBER_ID = UUID.fromString("2f0d7b64-2b3e-4f5a-8f5a-7f0f3b6d2a11");
    private static final UUID OTHER_MEMBER_ID = UUID.fromString("9a2f4c31-6d5b-4f2e-9c8d-1b0e7a4c5d22");
    private static final Instant NOW = Instant.parse("2026-09-07T00:00:00Z");

    private final AtomicInteger translateCalls = new AtomicInteger();

    @DisplayName("변환 결과를 그대로 돌려준다")
    @Test
    void returnTranslation() {
        AiStrategyTranslationService service = createService(Clock.fixed(NOW, ZoneOffset.UTC), 2);

        AiStrategyTranslation translation = service.translate(MEMBER_ID, "5% 이상 오른 코인 사줘", null);

        assertThat(translation.attempts()).isEqualTo(1);
        assertThat(translation.assumptions()).containsExactly("기본 시간프레임 1D를 사용했습니다.");
    }

    @DisplayName("회원당 분당 호출 한도를 넘으면 429로 막는다")
    @Test
    void rejectWhenMemberExceedsQuota() {
        AiStrategyTranslationService service = createService(Clock.fixed(NOW, ZoneOffset.UTC), 2);

        service.translate(MEMBER_ID, "첫 번째", null);
        service.translate(MEMBER_ID, "두 번째", null);

        assertThatThrownBy(() -> service.translate(MEMBER_ID, "세 번째", null))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getErrorCode())
                .isEqualTo(ApiErrorCode.AI_TRANSLATION_QUOTA_EXCEEDED);
        assertThat(translateCalls.get()).isEqualTo(2);
    }

    @DisplayName("한도는 회원별로 따로 센다")
    @Test
    void countQuotaPerMember() {
        AiStrategyTranslationService service = createService(Clock.fixed(NOW, ZoneOffset.UTC), 1);

        service.translate(MEMBER_ID, "첫 번째", null);
        service.translate(OTHER_MEMBER_ID, "첫 번째", null);

        assertThat(translateCalls.get()).isEqualTo(2);
    }

    @DisplayName("1분이 지난 호출은 한도에서 빠진다")
    @Test
    void forgetCallsOlderThanOneMinute() {
        MutableClock clock = new MutableClock(NOW);
        AiStrategyTranslationService service = createService(clock, 1);

        service.translate(MEMBER_ID, "첫 번째", null);
        clock.advance(Duration.ofSeconds(61));
        service.translate(MEMBER_ID, "두 번째", null);

        assertThat(translateCalls.get()).isEqualTo(2);
    }

    private AiStrategyTranslationService createService(Clock clock, int translationsPerMinute) {
        AiStrategyTranslator translator = (userInput, currentStrategy) -> {
            translateCalls.incrementAndGet();
            return new AiStrategyTranslation(
                    null,
                    1,
                    "test-model",
                    1_200L,
                    List.of(),
                    List.of("기본 시간프레임 1D를 사용했습니다."),
                    List.of()
            );
        };
        AiProperties properties = new AiProperties(
                "http://localhost:8000",
                Duration.ofSeconds(10),
                translationsPerMinute
        );
        return new AiStrategyTranslationService(translator, properties, clock);
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
