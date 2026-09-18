package com.ssafy.a107.contest.service;

import com.ssafy.a107.contest.domain.ContestDisplayStatus;
import com.ssafy.a107.contest.domain.ContestEntry;
import com.ssafy.a107.contest.domain.ContestRound;
import com.ssafy.a107.strategy.domain.dsl.Timeframe;

import java.util.Map;

/** 회차 상세. 채점 규칙 상수는 `ContestScoringPolicy` 를 그대로 쓴다. */
public record ContestRoundDetail(
        ContestRound round,
        ContestDisplayStatus status,
        long entryCount,
        ContestEntry myEntry,
        Map<Timeframe, Integer> factorPeriodCaps
) {
}
