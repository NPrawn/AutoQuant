package com.ssafy.a107.contest.controller.response;

import com.ssafy.a107.contest.domain.ContestDisqualificationReason;
import com.ssafy.a107.contest.domain.ContestResult;
import com.ssafy.a107.contest.domain.MarketDatasetKind;
import com.ssafy.a107.contest.service.ContestResultReadService.ContestDatasetOutcome;
import com.ssafy.a107.contest.service.ContestResultReadService.ContestMyResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ContestMyResultResponse(
        Integer ranking,
        BigDecimal totalScore,
        BigDecimal returnScore,
        BigDecimal stabilityScore,
        BigDecimal bullMddPercentile,
        BigDecimal bearMddPercentile,
        BigDecimal sidewaysMddPercentile,
        ContestDisqualificationReason disqualificationReason,
        MarketDatasetKind disqualifiedKind,
        int entryCount,
        BigDecimal topTotalScore,
        List<ContestDatasetOutcomeItem> datasets
) {

    public static ContestMyResultResponse from(ContestMyResult myResult) {
        ContestResult result = myResult.result();

        return new ContestMyResultResponse(
                result.getRanking(),
                result.getTotalScore(),
                result.getReturnScore(),
                result.getStabilityScore(),
                result.getBullMddPercentile(),
                result.getBearMddPercentile(),
                result.getSidewaysMddPercentile(),
                result.getDisqualificationReason(),
                result.getDisqualifiedKind(),
                myResult.entryCount(),
                myResult.topTotalScore(),
                myResult.datasets().stream().map(ContestDatasetOutcomeItem::from).toList()
        );
    }

    public record ContestDatasetOutcomeItem(
            MarketDatasetKind kind,
            Instant periodStart,
            Instant periodEnd,
            BigDecimal marketReturn,
            BigDecimal cumulativeReturn,
            BigDecimal maximumDrawdown,
            BigDecimal averageExposure,
            Integer tradeCount
    ) {

        static ContestDatasetOutcomeItem from(ContestDatasetOutcome outcome) {
            return new ContestDatasetOutcomeItem(
                    outcome.kind(),
                    outcome.periodStart(),
                    outcome.periodEnd(),
                    outcome.marketReturn(),
                    outcome.cumulativeReturn(),
                    outcome.maximumDrawdown(),
                    outcome.averageExposure(),
                    outcome.tradeCount()
            );
        }
    }
}
