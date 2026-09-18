package com.ssafy.a107.contest.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/*
 * 대회 결과 화면의 자산 곡선 한 점이다. 엔진은 전략 봉마다 자산을 기록해 1시간봉이면 13주에 2,184점이라
 * 하루에 한 점만 남겨 봉 간격과 무관하게 90점 남짓으로 줄인다.
 */
public record ContestEquityPoint(Instant valuationAt, BigDecimal returnRate) {

    public ContestEquityPoint {
        Objects.requireNonNull(valuationAt);
        Objects.requireNonNull(returnRate);
    }

    /*
     * UTC 하루마다 그날 닫힌 마지막 기록을 남긴다. 00:00 에 찍힌 기록은 전날 마지막 봉이 닫힌 값이라 전날로 센다.
     * points 는 시각 순이어야 한다.
     */
    public static List<ContestEquityPoint> lastOfEachDay(List<ContestEquityPoint> points) {
        Map<LocalDate, ContestEquityPoint> days = new LinkedHashMap<>();
        for (ContestEquityPoint point : points) {
            LocalDate closedOn = LocalDate.ofInstant(point.valuationAt().minusNanos(1), ZoneOffset.UTC);
            days.put(closedOn, point);
        }
        return new ArrayList<>(days.values());
    }
}
