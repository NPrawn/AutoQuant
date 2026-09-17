package com.ssafy.a107.contest.domain;

import java.math.BigDecimal;

/*
 * 채점 규칙 상수다. 회차 상세 API 가 참가자에게 그대로 보여 주고, 채점도 같은 값을 쓴다
 * (ADR-0002, ADR-0003, 대회 기능 설계 7절).
 */
public final class ContestScoringPolicy {

    public static final BigDecimal RETURN_WEIGHT = new BigDecimal("0.5");
    public static final BigDecimal STABILITY_WEIGHT = new BigDecimal("0.5");
    public static final BigDecimal STABILITY_MEAN_WEIGHT = new BigDecimal("0.6");
    public static final BigDecimal STABILITY_WORST_WEIGHT = new BigDecimal("0.4");

    public static final BigDecimal MINIMUM_EXPOSURE = new BigDecimal("0.30");

    public static final BigDecimal INITIAL_CAPITAL = new BigDecimal("10000000");
    public static final BigDecimal FEE_RATE = new BigDecimal("0.0005");
    public static final BigDecimal SLIPPAGE_BPS = new BigDecimal("10");

    private ContestScoringPolicy() {
    }
}
