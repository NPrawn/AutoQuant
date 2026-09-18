package com.ssafy.a107.contest.service;

import com.ssafy.a107.contest.domain.ContestDisplayStatus;
import com.ssafy.a107.contest.domain.ContestEntry;
import com.ssafy.a107.contest.domain.ContestResult;
import com.ssafy.a107.contest.domain.ContestRound;
import com.ssafy.a107.contest.repository.ContestRoundTopResult;

/*
 * 목록 한 줄. `myEntry` 는 제출하지 않았으면, `myResult` 는 아직 채점 전이면, `top` 은 채점된 제출이
 * 하나도 없으면 null 이다. 점수는 절대값이라 마감 전에도 채점이 끝난 제출부터 값이 있다 (ADR-0003).
 */
public record ContestRoundSummary(
        ContestRound round,
        ContestDisplayStatus status,
        long entryCount,
        ContestEntry myEntry,
        ContestResult myResult,
        ContestRoundTopResult top
) {
}
