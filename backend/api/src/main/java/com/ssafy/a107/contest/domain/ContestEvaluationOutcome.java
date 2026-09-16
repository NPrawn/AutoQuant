package com.ssafy.a107.contest.domain;

import java.math.BigDecimal;

/*
 * 채점이 보는 대회 백테스트 한 건의 결과다. 실패한 백테스트는 completed 가 false 이고 값이 비어 있다.
 */
public record ContestEvaluationOutcome(
        boolean completed,
        BigDecimal cumulativeReturn,
        BigDecimal maximumDrawdown,
        BigDecimal averageExposure
) {

    public static ContestEvaluationOutcome failed() {
        return new ContestEvaluationOutcome(false, null, null, null);
    }

    public static ContestEvaluationOutcome completed(
            BigDecimal cumulativeReturn,
            BigDecimal maximumDrawdown,
            BigDecimal averageExposure
    ) {
        return new ContestEvaluationOutcome(true, cumulativeReturn, maximumDrawdown, averageExposure);
    }
}
