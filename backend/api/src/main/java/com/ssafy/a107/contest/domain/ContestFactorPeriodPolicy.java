package com.ssafy.a107.contest.domain;

import com.ssafy.a107.strategy.domain.dsl.Timeframe;

import java.util.LinkedHashMap;
import java.util.Map;

/*
 * 대회는 제공한 데이터 안에서만 한다. 팩터 기간은 워밍업 캔들 수보다 짧아야 한다 (ADR-0013).
 * 워밍업 5주면 1시간봉 839, 4시간봉 209, 일봉 34다.
 */
public final class ContestFactorPeriodPolicy {

    private static final int HOURS_PER_WEEK = 7 * 24;

    private ContestFactorPeriodPolicy() {
    }

    public static Map<Timeframe, Integer> caps(int warmupWeeks) {
        if (warmupWeeks <= 0) {
            throw new IllegalArgumentException("Warmup weeks must be positive");
        }

        int warmupHours = warmupWeeks * HOURS_PER_WEEK;
        Map<Timeframe, Integer> caps = new LinkedHashMap<>();
        for (Timeframe timeframe : Timeframe.values()) {
            caps.put(timeframe, warmupHours / (timeframe.getIntervalMinutes() / 60) - 1);
        }
        return Map.copyOf(caps);
    }

    public static int cap(int warmupWeeks, Timeframe timeframe) {
        return caps(warmupWeeks).get(timeframe);
    }
}
