package com.ssafy.a107.trading.backtest.engine;

import com.ssafy.a107.strategy.domain.dsl.RebalancePeriod;
import com.ssafy.a107.strategy.domain.dsl.Timeframe;
import org.springframework.stereotype.Component;

import java.time.*;
import java.util.Objects;

@Component
public class BacktestRebalanceSchedule {

    public boolean shouldRebalance(
            RebalancePeriod rebalancePeriod,
            Timeframe timeframe,
            Instant candleOpenTime,
            boolean firstEvaluationCandle
    ) {
        Objects.requireNonNull(rebalancePeriod);
        Objects.requireNonNull(timeframe);
        Objects.requireNonNull(candleOpenTime);

        if (firstEvaluationCandle) {
            return true;
        }

        OffsetDateTime candleCloseTime = candleOpenTime
                .plus(candleDuration(timeframe))
                .atOffset(ZoneOffset.UTC);

        if (!candleCloseTime.toLocalTime().equals(LocalTime.MIDNIGHT)) {
            return false;
        }

        return switch (rebalancePeriod) {
            case DAILY -> true;
            case WEEKLY -> candleCloseTime.getDayOfWeek() == DayOfWeek.MONDAY;
            case MONTHLY -> candleCloseTime.getDayOfMonth() == 1;
        };

    }

    private Duration candleDuration(Timeframe timeframe) {
        return Duration.ofMinutes(timeframe.getIntervalMinutes());
    }
}
