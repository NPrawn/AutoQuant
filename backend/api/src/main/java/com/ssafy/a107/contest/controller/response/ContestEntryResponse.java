package com.ssafy.a107.contest.controller.response;

import com.ssafy.a107.contest.domain.ContestEntry;

import java.time.Instant;
import java.util.UUID;

public record ContestEntryResponse(
        UUID roundId,
        Long strategyId,
        String strategyName,
        Instant submittedAt
) {

    public static ContestEntryResponse from(ContestEntry entry) {
        return new ContestEntryResponse(
                entry.getRound().getRoundId(),
                entry.getStrategy().getStrategyId(),
                entry.getStrategy().getName(),
                entry.getSubmittedAt()
        );
    }
}
