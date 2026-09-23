package com.ssafy.a107.contest.controller.response;

import com.ssafy.a107.contest.domain.ContestEntry;

import java.time.Instant;

/** 회차 상세에 싣는 내 마지막 제출이다. 제출마다 행이 쌓이므로 그 앞의 제출은 순위 목록에 있다. */
public record ContestSubmissionResponse(
        Long strategyId,
        String strategyName,
        Instant submittedAt
) {

    public static ContestSubmissionResponse from(ContestEntry entry) {
        if (entry == null) {
            return null;
        }

        return new ContestSubmissionResponse(
                entry.getStrategy().getStrategyId(),
                entry.getStrategy().getName(),
                entry.getSubmittedAt()
        );
    }
}
