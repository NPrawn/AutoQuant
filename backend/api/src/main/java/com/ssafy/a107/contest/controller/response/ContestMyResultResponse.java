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
        Long strategyId,
        String strategyName,
        Integer ranking,
        BigDecimal totalScore,
        BigDecimal returnScore,
        BigDecimal stabilityScore,
        BigDecimal bullRegimeScore,
        BigDecimal bearRegimeScore,
        BigDecimal sidewaysRegimeScore,
        ContestDisqualificationReason disqualificationReason,
        MarketDatasetKind disqualifiedKind,
        int entryCount,
        BigDecimal topTotalScore,
        int finishedBacktests,
        int totalBacktests,
        List<ContestDatasetOutcomeItem> datasets
) {

    /* 백테스트가 도는 동안에는 점수 자리가 비어 있고 진행 수만 채워진다. */
    public static ContestMyResultResponse from(ContestMyResult myResult) {
        ContestResult result = myResult.result();

        return new ContestMyResultResponse(
                myResult.strategyId(),
                myResult.strategyName(),
                result == null ? null : result.getRanking(),
                result == null ? null : result.getTotalScore(),
                result == null ? null : result.getReturnScore(),
                result == null ? null : result.getStabilityScore(),
                result == null ? null : result.getBullRegimeScore(),
                result == null ? null : result.getBearRegimeScore(),
                result == null ? null : result.getSidewaysRegimeScore(),
                result == null ? null : result.getDisqualificationReason(),
                result == null ? null : result.getDisqualifiedKind(),
                myResult.entryCount(),
                myResult.topTotalScore(),
                myResult.finishedBacktests(),
                myResult.totalBacktests(),
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
