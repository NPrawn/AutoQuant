package com.ssafy.a107.ai.controller.response;

import com.ssafy.a107.ai.service.port.AiStrategyTranslation;
import com.ssafy.a107.strategy.domain.dsl.StrategyDsl;

import java.util.List;

public record TranslateStrategyResponse(
        StrategyDsl strategy,
        Meta meta
) {

    public static TranslateStrategyResponse from(AiStrategyTranslation translation) {
        return new TranslateStrategyResponse(
                translation.strategy(),
                new Meta(
                        translation.attempts(),
                        translation.elapsedMs(),
                        translation.warnings(),
                        translation.assumptions(),
                        translation.changes()
                )
        );
    }

    /* AI 서버의 model 은 밖으로 내보내지 않는다. 사용자가 쓸 데가 없고 공급자 교체가 계약 변경이 된다. */
    public record Meta(
            int attempts,
            long elapsedMs,
            List<String> warnings,
            List<String> assumptions,
            List<String> changes
    ) {

        public Meta {
            warnings = List.copyOf(warnings);
            assumptions = List.copyOf(assumptions);
            changes = List.copyOf(changes);
        }
    }
}
