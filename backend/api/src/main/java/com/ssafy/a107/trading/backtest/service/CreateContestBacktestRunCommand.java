package com.ssafy.a107.trading.backtest.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/*
 * 채점이 만드는 대회 백테스트다. 제출한 전략을 데이터셋 한 벌에 돌린다 (ADR-0014).
 * 체결 규칙은 다음 봉 시가로 고정한다.
 */
public record CreateContestBacktestRunCommand(
        Long strategyId,
        UUID contestDatasetId,
        Instant periodStart,
        Instant periodEnd,
        BigDecimal initialCapital,
        BigDecimal feeRate,
        BigDecimal slippageBps,
        String engineVersion,
        String marketDataVersion
) {
}
