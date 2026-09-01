package com.ssafy.a107.strategy.domain.dsl;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record ConditionFilterNode(
        @NotNull(message = "CONDITION left operand is required")
        @Valid
        Operand left,

        @NotNull(message = "CONDITION operator is required")
        ComparisonOperator operator,

        @NotNull(message = "CONDITION right operand is required")
        @Valid
        Operand right
) implements FilterNode {
}
