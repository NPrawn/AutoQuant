package com.ssafy.a107.contest.controller.response;

import com.ssafy.a107.contest.domain.ContestEntry;

import java.time.Instant;
import java.util.UUID;

public record ContestEntryResponse(
        UUID roundId,
        Long strategyId,
        String strategyName,
        int submissionCount,
        int remainingSubmissions,
        Instant firstSubmittedAt,
        Instant lastSubmittedAt
) {

    public static ContestEntryResponse from(ContestEntry entry) {
        int maxSubmissions = entry.getRound().getMaxSubmissions();

        return new ContestEntryResponse(
                entry.getRound().getRoundId(),
                entry.getStrategy().getStrategyId(),
                entry.getStrategy().getName(),
                entry.getSubmissionCount(),
                entry.remainingSubmissions(maxSubmissions),
                entry.getFirstSubmittedAt(),
                entry.getLastSubmittedAt()
        );
    }
}
