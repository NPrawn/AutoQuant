package com.ssafy.a107.strategy.domain.dsl;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record GroupFilterNode(
        @NotNull(message = "GROUP logic is required")
        Logic logic,

        @NotEmpty(message = "GROUP children must not be empty")
        List<@NotNull @Valid FilterNode> children
) implements FilterNode {
}
