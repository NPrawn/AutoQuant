package com.ssafy.a107.strategy.domain.dsl;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record StrategyDsl(
        @NotNull(message = "Strategy timeframe is required")
        Timeframe timeframe,

        @NotNull(message = "Strategy filters are required")
        @Valid
        FilterNode filters,

        @NotNull(message = "Strategy ranking is required")
        @Valid
        Ranking ranking,

        @NotNull(message = "Strategy selection is required")
        @Valid
        Selection selection,

        @NotNull(message = "Strategy rebalance is required")
        @Valid
        Rebalance rebalance
) {
}
