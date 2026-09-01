package com.ssafy.a107.strategy.controller.request;

import com.ssafy.a107.strategy.domain.dsl.StrategyDsl;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateStrategyRequest(
        @NotBlank(message = "전략 이름은 필수입니다.")
        @Size(max = 100, message = "전략 이름은 100자 이하여야 합니다.")
        String name,

        String description,

        @NotNull(message = "전략 DSL은 필수입니다.")
        @Valid
        StrategyDsl strategyDsl
) {
}
