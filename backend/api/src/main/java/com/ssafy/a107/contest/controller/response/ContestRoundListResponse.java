package com.ssafy.a107.contest.controller.response;

import com.ssafy.a107.contest.domain.ContestDisplayStatus;
import com.ssafy.a107.contest.service.ContestRoundSummary;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ContestRoundListResponse(
        List<ContestRoundItem> contests
) {

    public static ContestRoundListResponse from(List<ContestRoundSummary> summaries) {
        return new ContestRoundListResponse(summaries.stream().map(ContestRoundItem::from).toList());
    }

    public record ContestRoundItem(
            UUID roundId,
            String title,
            ContestDisplayStatus status,
            Instant submissionStartsAt,
            Instant submissionEndsAt,
            long entryCount,
            ContestSubmissionResponse mySubmission
    ) {

        static ContestRoundItem from(ContestRoundSummary summary) {
            return new ContestRoundItem(
                    summary.round().getRoundId(),
                    summary.round().getTitle(),
                    summary.status(),
                    summary.round().getSubmissionStartsAt(),
                    summary.round().getSubmissionEndsAt(),
                    summary.entryCount(),
                    ContestSubmissionResponse.from(summary.myEntry(), summary.round().getMaxSubmissions())
            );
        }
    }
}
