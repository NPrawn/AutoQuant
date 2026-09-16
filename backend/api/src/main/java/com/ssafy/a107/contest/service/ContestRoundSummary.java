package com.ssafy.a107.contest.service;

import com.ssafy.a107.contest.domain.ContestDisplayStatus;
import com.ssafy.a107.contest.domain.ContestEntry;
import com.ssafy.a107.contest.domain.ContestRound;

/** 목록 한 줄. `myEntry` 는 제출하지 않았으면 null 이다. */
public record ContestRoundSummary(
        ContestRound round,
        ContestDisplayStatus status,
        long entryCount,
        ContestEntry myEntry
) {
}
