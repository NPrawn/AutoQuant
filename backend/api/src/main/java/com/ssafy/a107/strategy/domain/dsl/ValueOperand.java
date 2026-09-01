package com.ssafy.a107.strategy.domain.dsl;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ValueOperand(
        @NotNull(message = "VALUE value is required")
        BigDecimal value
) implements Operand {
}
