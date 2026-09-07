package com.ssafy.a107.ai.infrastructure;

import com.ssafy.a107.strategy.domain.dsl.StrategyDsl;

import java.util.List;

record AiTranslateResponse(StrategyDsl strategy, Meta meta) {

    record Meta(
            Integer attempts,
            String model,
            Long elapsedMs,
            List<String> warnings,
            List<String> assumptions,
            List<String> changes
    ) {
    }
}
