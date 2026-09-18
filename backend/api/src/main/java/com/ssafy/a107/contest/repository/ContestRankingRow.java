package com.ssafy.a107.contest.repository;

import java.math.BigDecimal;
import java.util.UUID;

/*
 * 랭킹 한 줄. 제출(contest_entry)과 채점 결과(contest_result)를 한 번에 읽는 조회 전용 형태다.
 * memberId 는 "내 제출"을 가리기 위한 값이라 응답에는 내보내지 않는다.
 */
public record ContestRankingRow(
        Integer ranking,
        Long strategyId,
        String strategyName,
        String nickname,
        boolean published,
        BigDecimal totalScore,
        BigDecimal returnScore,
        BigDecimal stabilityScore,
        BigDecimal bullRegimeScore,
        BigDecimal bearRegimeScore,
        BigDecimal sidewaysRegimeScore,
        UUID memberId
) {
}
