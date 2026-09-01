package com.ssafy.a107.strategy.domain.dsl;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record Selection(
        @NotNull(message = "Selection topN is required")
        @Positive(message = "Selection topN must be greater than zero")
        Integer topN
) {
}
