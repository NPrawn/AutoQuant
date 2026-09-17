package com.ssafy.a107.contest.repository;

import java.math.BigDecimal;
import java.util.UUID;

/*
 * 회차 1위 한 줄. 목록 화면(목업 30)의 지난 회차 표가 쓴다. 동점이 있으면 그중 하나가 온다.
 */
public record ContestRoundTopResult(
        UUID roundId,
        String strategyName,
        String nickname,
        BigDecimal totalScore
) {
}
