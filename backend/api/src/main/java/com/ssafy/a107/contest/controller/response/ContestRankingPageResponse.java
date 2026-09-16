package com.ssafy.a107.contest.controller.response;

import com.ssafy.a107.contest.repository.ContestRankingRow;
import com.ssafy.a107.contest.service.ContestResultReadService.ContestRankings;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/*
 * 랭킹 화면(목업 06)의 표 한 페이지. 실격 제출은 순위가 없어 목록에서 빼고 수만 알려 준다.
 * 다른 참가자의 회원 식별자는 내보내지 않고 내 제출인지만 표시한다.
 */
public record ContestRankingPageResponse(
        List<ContestRankingItem> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        long disqualifiedCount
) {

    public static ContestRankingPageResponse from(ContestRankings rankings, UUID memberId) {
        Page<ContestRankingRow> rows = rankings.page();

        return new ContestRankingPageResponse(
                rows.getContent().stream().map(row -> ContestRankingItem.from(row, memberId)).toList(),
                rows.getNumber(),
                rows.getSize(),
                rows.getTotalElements(),
                rows.getTotalPages(),
                rankings.disqualifiedCount()
        );
    }

    public record ContestRankingItem(
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
            boolean mine
    ) {

        static ContestRankingItem from(ContestRankingRow row, UUID memberId) {
            return new ContestRankingItem(
                    row.ranking(),
                    row.strategyId(),
                    row.strategyName(),
                    row.nickname(),
                    row.published(),
                    row.totalScore(),
                    row.returnScore(),
                    row.stabilityScore(),
                    row.bullRegimeScore(),
                    row.bearRegimeScore(),
                    row.sidewaysRegimeScore(),
                    row.memberId().equals(memberId)
            );
        }
    }
}
