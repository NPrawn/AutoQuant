package com.ssafy.a107.contest.controller.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record SubmitContestEntryRequest(
        @NotNull(message = "Strategy id is required")
        @Positive(message = "Strategy id must be positive")
        Long strategyId
) {
}
