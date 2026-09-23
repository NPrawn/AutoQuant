package com.ssafy.a107.contest.repository;

import java.math.BigDecimal;
import java.util.UUID;

/*
 * 랭킹 한 줄. 제출(contest_entry)과 채점 결과(contest_result)를 한 번에 읽는 조회 전용 형태다.
 * memberId 는 "내 제출"을 가리기 위한 값이라 응답에는 내보내지 않는다.
 *
 * strategyName 은 제출한 판의 이름이다. 읽는 쪽이 뿌리 기준 최신 이름으로 바꿔 넣는다 (withName).
 * versionNo 는 제출한 판 그대로다. 채점된 것이 어느 판인지 가리키는 값이라 따라가면 안 된다.
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
        BigDecimal generalReturn,
        BigDecimal generalMdd,
        Long rootStrategyId,
        int versionNo,
        UUID memberId
) {

    public ContestRankingRow withName(String latestName) {
        return new ContestRankingRow(
                ranking, strategyId, latestName, nickname, published,
                totalScore, returnScore, stabilityScore,
                bullRegimeScore, bearRegimeScore, sidewaysRegimeScore,
                generalReturn, generalMdd, rootStrategyId, versionNo, memberId);
    }
}
