package com.ssafy.a107.trading.backtest.engine;

import com.ssafy.a107.strategy.domain.dsl.RebalancePeriod;
import com.ssafy.a107.strategy.domain.dsl.Timeframe;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class BacktestRebalanceScheduleTest {

    private final BacktestRebalanceSchedule schedule = new BacktestRebalanceSchedule();

    @DisplayName("최초 평가 봉은 리밸런싱 주기와 관계없이 리밸런싱한다")
    @Test
    void rebalanceOnFirstEvaluationCandle() {
        boolean shouldRebalance = schedule.shouldRebalance(
                RebalancePeriod.MONTHLY,
                Timeframe.ONE_HOUR,
                Instant.parse("2026-01-14T10:00:00Z"),
                true
        );

        assertThat(shouldRebalance).isTrue();
    }

    @DisplayName("일간 리밸런싱은 UTC 자정에 종료되는 봉에서 실행한다")
    @Test
    void rebalanceDailyAtUtcMidnight() {
        boolean shouldRebalance = schedule.shouldRebalance(
                RebalancePeriod.DAILY,
                Timeframe.ONE_HOUR,
                Instant.parse("2026-01-14T23:00:00Z"),
                false
        );

        assertThat(shouldRebalance).isTrue();
    }

    @DisplayName("일간 리밸런싱은 UTC 자정에 종료되지 않는 봉에서 실행하지 않는다")
    @Test
    void doNotRebalanceDailyBeforeUtcMidnight() {
        boolean shouldRebalance = schedule.shouldRebalance(
                RebalancePeriod.DAILY,
                Timeframe.ONE_HOUR,
                Instant.parse("2026-01-14T22:00:00Z"),
                false
        );

        assertThat(shouldRebalance).isFalse();
    }

    @DisplayName("주간 리밸런싱은 월요일 UTC 자정에 종료되는 봉에서 실행한다")
    @Test
    void rebalanceWeeklyAtMondayUtcMidnight() {
        boolean shouldRebalance = schedule.shouldRebalance(
                RebalancePeriod.WEEKLY,
                Timeframe.ONE_HOUR,
                Instant.parse("2026-01-04T23:00:00Z"),
                false
        );

        assertThat(shouldRebalance).isTrue();
    }

    @DisplayName("월간 리밸런싱은 매월 1일 UTC 자정에 종료되는 봉에서 실행한다")
    @Test
    void rebalanceMonthlyAtFirstDayUtcMidnight() {
        boolean shouldRebalance = schedule.shouldRebalance(
                RebalancePeriod.MONTHLY,
                Timeframe.ONE_HOUR,
                Instant.parse("2026-02-28T23:00:00Z"),
                false
        );

        assertThat(shouldRebalance).isTrue();
    }
}
