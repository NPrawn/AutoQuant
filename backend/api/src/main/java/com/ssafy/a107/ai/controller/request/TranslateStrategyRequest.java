package com.ssafy.a107.ai.controller.request;

import com.ssafy.a107.strategy.domain.dsl.StrategyDsl;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TranslateStrategyRequest(
        @NotBlank(message = "전략 설명은 필수입니다.")
        @Size(max = 500, message = "전략 설명은 500자 이하여야 합니다.")
        String userInput,

        /* 비어 있으면 새 전략을 만들고, 있으면 그 전략을 고친다. */
        @Valid
        StrategyDsl currentStrategy
) {
}
