package com.ssafy.a107.strategy.domain.dsl;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record Ranking(
        @NotNull(message = "Ranking factor is required")
        FactorType factor,

        @NotNull(message = "Ranking period is required")
        @Positive(message = "Ranking period must be greater than zero")
        Integer period,

        @NotNull(message = "Ranking direction is required")
        RankingDirection direction
) {
}
