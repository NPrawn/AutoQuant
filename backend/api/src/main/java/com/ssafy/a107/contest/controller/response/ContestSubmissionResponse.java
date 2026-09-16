package com.ssafy.a107.contest.controller.response;

import com.ssafy.a107.contest.domain.ContestEntry;

import java.time.Instant;

public record ContestSubmissionResponse(
        Long strategyId,
        String strategyName,
        int submissionCount,
        int remainingSubmissions,
        Instant firstSubmittedAt,
        Instant lastSubmittedAt
) {

    public static ContestSubmissionResponse from(ContestEntry entry, int maxSubmissions) {
        if (entry == null) {
            return null;
        }

        return new ContestSubmissionResponse(
                entry.getStrategy().getStrategyId(),
                entry.getStrategy().getName(),
                entry.getSubmissionCount(),
                entry.remainingSubmissions(maxSubmissions),
                entry.getFirstSubmittedAt(),
                entry.getLastSubmittedAt()
        );
    }
}
