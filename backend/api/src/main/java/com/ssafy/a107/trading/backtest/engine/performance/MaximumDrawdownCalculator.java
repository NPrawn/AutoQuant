package com.ssafy.a107.trading.backtest.engine.performance;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

@Component
public class MaximumDrawdownCalculator {

    public BigDecimal calculate(List<EquitySnapshot> snapshots) {
        Objects.requireNonNull(snapshots);

        if (snapshots.isEmpty()) {
            throw new IllegalArgumentException(
                    "Equity snapshots must not be empty"
            );
        }

        BigDecimal maximumDrawdown = BigDecimal.ZERO;

        for (EquitySnapshot snapshot : snapshots) {
            Objects.requireNonNull(snapshot);

            if (snapshot.drawdownRate().compareTo(maximumDrawdown) > 0) {
                maximumDrawdown = snapshot.drawdownRate();
            }
        }

        return maximumDrawdown;
    }
}