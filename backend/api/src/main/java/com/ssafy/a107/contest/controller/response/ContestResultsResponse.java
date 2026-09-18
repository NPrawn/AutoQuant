package com.ssafy.a107.contest.controller.response;

import com.ssafy.a107.contest.domain.ContestDisqualificationReason;
import com.ssafy.a107.contest.domain.ContestNickname;
import com.ssafy.a107.contest.domain.MarketDatasetKind;
import com.ssafy.a107.contest.service.ContestResultReadService.ContestRanking;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ContestResultsResponse(
        List<ContestRankingItem> rankings
) {

    public static ContestResultsResponse from(List<ContestRanking> rankings, UUID memberId) {
        return new ContestResultsResponse(
                rankings.stream().map(ranking -> ContestRankingItem.from(ranking, memberId)).toList());
    }

    public record ContestRankingItem(
            Integer ranking,
            String nickname,
            String strategyName,
            BigDecimal totalScore,
            BigDecimal returnScore,
            BigDecimal stabilityScore,
            ContestDisqualificationReason disqualificationReason,
            MarketDatasetKind disqualifiedKind
    ) {

        static ContestRankingItem from(ContestRanking ranking, UUID memberId) {
            return new ContestRankingItem(
                    ranking.ranking(),
                    ContestNickname.visibleTo(ranking.nickname(), ranking.memberId(), memberId),
                    ranking.strategyName(),
                    ranking.totalScore(),
                    ranking.returnScore(),
                    ranking.stabilityScore(),
                    ranking.disqualificationReason(),
                    ranking.disqualifiedKind()
            );
        }
    }
}
