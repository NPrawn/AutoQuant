package com.ssafy.a107.contest.domain;

import java.math.BigDecimal;

/*
 * 채점이 보는 대회 백테스트 한 건의 결과다. 실패한 백테스트는 completed 가 false 이고 값이 비어 있다.
 * marketMaximumDrawdown 은 그 데이터셋의 시장 낙폭을 전략 봉 간격으로 잰 값이다.
 */
public record ContestEvaluationOutcome(
        boolean completed,
        BigDecimal cumulativeReturn,
        BigDecimal maximumDrawdown,
        BigDecimal averageExposure,
        BigDecimal marketMaximumDrawdown
) {

    public static ContestEvaluationOutcome failed() {
        return new ContestEvaluationOutcome(false, null, null, null, null);
    }

    public static ContestEvaluationOutcome completed(
            BigDecimal cumulativeReturn,
            BigDecimal maximumDrawdown,
            BigDecimal averageExposure,
            BigDecimal marketMaximumDrawdown
    ) {
        return new ContestEvaluationOutcome(
                true, cumulativeReturn, maximumDrawdown, averageExposure, marketMaximumDrawdown);
    }
}
