package com.ssafy.a107.strategy.domain.dsl;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record FactorOperand(
        @NotNull(message = "FACTOR factor is required")
        FactorType factor,

        @NotNull(message = "FACTOR period is required")
        @Positive(message = "FACTOR period must be greater than zero")
        Integer period
) implements Operand {
}
