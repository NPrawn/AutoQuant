package com.ssafy.a107.ai.service.port;

import com.ssafy.a107.strategy.domain.dsl.StrategyDsl;

import java.util.List;

/**
 * AI 서버가 돌려준 전략 초안과 그 판단 근거.
 *
 * <p>{@code strategy}는 비어 있을 수 있다. 전략 설명이 아닌 입력에는 AI 서버가 LLM을 부르지 않고
 * 전략 없이 {@code assumptions}만 돌려준다. 이때도 오류가 아니라 정상 응답이다.
 */
public record AiStrategyTranslation(
        StrategyDsl strategy,
        int attempts,
        String model,
        long elapsedMs,
        List<String> warnings,
        List<String> assumptions,
        List<String> changes
) {

    public AiStrategyTranslation {
        warnings = List.copyOf(warnings);
        assumptions = List.copyOf(assumptions);
        changes = List.copyOf(changes);
    }
}
