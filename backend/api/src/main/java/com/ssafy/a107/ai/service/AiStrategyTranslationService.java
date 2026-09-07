package com.ssafy.a107.ai.service;

import com.ssafy.a107.ai.infrastructure.AiProperties;
import com.ssafy.a107.ai.service.port.AiStrategyTranslation;
import com.ssafy.a107.ai.service.port.AiStrategyTranslator;
import com.ssafy.a107.common.exception.ApiErrorCode;
import com.ssafy.a107.common.exception.ApiException;
import com.ssafy.a107.strategy.domain.dsl.StrategyDsl;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AiStrategyTranslationService {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final AiStrategyTranslator translator;
    private final AiProperties properties;
    private final Clock clock;

    /*
     * ponytail: 프로세스 안의 호출 기록이라 인스턴스를 늘리면 회원당 한도가 인스턴스 수만큼 늘어난다.
     * 다중화하면 Redis(이미 의존성에 있다)로 옮긴다.
     */
    private final Map<UUID, Deque<Long>> callTimestamps = new ConcurrentHashMap<>();

    public AiStrategyTranslationService(
            AiStrategyTranslator translator,
            AiProperties properties,
            Clock clock
    ) {
        this.translator = translator;
        this.properties = properties;
        this.clock = clock;
    }

    public AiStrategyTranslation translate(UUID memberId, String userInput, StrategyDsl currentStrategy) {
        recordCallOrReject(memberId);
        return translator.translate(userInput, currentStrategy);
    }

    private void recordCallOrReject(UUID memberId) {
        long now = clock.millis();
        long cutoff = now - WINDOW.toMillis();
        Deque<Long> timestamps = callTimestamps.computeIfAbsent(memberId, key -> new ArrayDeque<>());

        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst() <= cutoff) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= properties.translationsPerMinutePerMember()) {
                throw new ApiException(ApiErrorCode.AI_TRANSLATION_QUOTA_EXCEEDED);
            }
            timestamps.addLast(now);
        }
    }
}
