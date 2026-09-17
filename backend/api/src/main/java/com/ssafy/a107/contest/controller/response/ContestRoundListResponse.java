package com.ssafy.a107.contest.controller.response;

import com.ssafy.a107.contest.domain.ContestDisplayStatus;
import com.ssafy.a107.contest.domain.ContestNickname;
import com.ssafy.a107.contest.domain.ContestResult;
import com.ssafy.a107.contest.repository.ContestRoundTopResult;
import com.ssafy.a107.contest.service.ContestRoundSummary;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ContestRoundListResponse(
        List<ContestRoundItem> contests
) {

    public static ContestRoundListResponse from(List<ContestRoundSummary> summaries, UUID memberId) {
        return new ContestRoundListResponse(
                summaries.stream().map(summary -> ContestRoundItem.from(summary, memberId)).toList());
    }

    /*
     * 지난 회차 표(목업 30)가 내 순위와 1위를 함께 보여 준다. 채점 전이거나 실격이면 내 값이 비고,
     * 채점된 제출이 하나도 없으면 1위가 빈다.
     */
    public record ContestRoundItem(
            UUID roundId,
            String title,
            ContestDisplayStatus status,
            Instant submissionStartsAt,
            Instant submissionEndsAt,
            long entryCount,
            ContestSubmissionResponse mySubmission,
            Integer myRanking,
            BigDecimal myTotalScore,
            String topStrategyName,
            String topNickname,
            BigDecimal topTotalScore
    ) {

        static ContestRoundItem from(ContestRoundSummary summary, UUID memberId) {
            ContestResult myResult = summary.myResult();
            ContestRoundTopResult top = summary.top();

            return new ContestRoundItem(
                    summary.round().getRoundId(),
                    summary.round().getTitle(),
                    summary.status(),
                    summary.round().getSubmissionStartsAt(),
                    summary.round().getSubmissionEndsAt(),
                    summary.entryCount(),
                    ContestSubmissionResponse.from(summary.myEntry(), summary.round().getMaxSubmissions()),
                    myResult == null ? null : myResult.getRanking(),
                    myResult == null ? null : myResult.getTotalScore(),
                    top == null ? null : top.strategyName(),
                    top == null ? null : ContestNickname.visibleTo(top.nickname(), top.memberId(), memberId),
                    top == null ? null : top.totalScore()
            );
        }
    }
}
