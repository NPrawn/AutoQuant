package com.ssafy.a107.strategy.domain.dsl;

import jakarta.validation.constraints.NotNull;

public record Rebalance(
        @NotNull(message = "Rebalance period is required")
        RebalancePeriod period
) {
}
