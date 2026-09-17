package com.ssafy.a107.contest.controller.response;

import com.ssafy.a107.contest.domain.ContestDisplayStatus;
import com.ssafy.a107.contest.domain.ContestScoringPolicy;
import com.ssafy.a107.contest.service.ContestRoundDetail;
import com.ssafy.a107.strategy.domain.dsl.Timeframe;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record ContestRoundDetailResponse(
        UUID roundId,
        String title,
        ContestDisplayStatus status,
        Instant submissionStartsAt,
        Instant submissionEndsAt,
        int maxSubmissions,
        int warmupWeeks,
        int evaluationWeeks,
        long entryCount,
        Map<String, Integer> factorPeriodCaps,
        ContestScoringRulesResponse scoringRules,
        ContestSubmissionResponse mySubmission
) {

    public static ContestRoundDetailResponse from(ContestRoundDetail detail) {
        Map<String, Integer> caps = new LinkedHashMap<>();
        for (Map.Entry<Timeframe, Integer> cap : detail.factorPeriodCaps().entrySet()) {
            caps.put(cap.getKey().getValue(), cap.getValue());
        }

        return new ContestRoundDetailResponse(
                detail.round().getRoundId(),
                detail.round().getTitle(),
                detail.status(),
                detail.round().getSubmissionStartsAt(),
                detail.round().getSubmissionEndsAt(),
                detail.round().getMaxSubmissions(),
                detail.round().getWarmupWeeks(),
                detail.round().getEvaluationWeeks(),
                detail.entryCount(),
                Map.copyOf(caps),
                ContestScoringRulesResponse.defaults(),
                ContestSubmissionResponse.from(detail.myEntry(), detail.round().getMaxSubmissions())
        );
    }

    public record ContestScoringRulesResponse(
            BigDecimal returnWeight,
            BigDecimal stabilityWeight,
            BigDecimal minimumExposure,
            BigDecimal initialCapital,
            BigDecimal feeRate,
            BigDecimal slippageBps
    ) {

        static ContestScoringRulesResponse defaults() {
            return new ContestScoringRulesResponse(
                    ContestScoringPolicy.RETURN_WEIGHT,
                    ContestScoringPolicy.STABILITY_WEIGHT,
                    ContestScoringPolicy.MINIMUM_EXPOSURE,
                    ContestScoringPolicy.INITIAL_CAPITAL,
                    ContestScoringPolicy.FEE_RATE,
                    ContestScoringPolicy.SLIPPAGE_BPS
            );
        }
    }
}
