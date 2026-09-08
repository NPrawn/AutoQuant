package com.ssafy.a107.trading.backtest.engine.performance;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
public class PeriodicReturnCalculator {

    public List<BigDecimal> calculate(List<EquitySnapshot> snapshots) {
        Objects.requireNonNull(snapshots);

        if (snapshots.size() < 2) {
            throw new IllegalArgumentException(
                    "At least two equity snapshots are required"
            );
        }

        List<BigDecimal> periodicReturns = new ArrayList<>(
                snapshots.size() - 1
        );

        for (int index = 1; index < snapshots.size(); index++) {
            EquitySnapshot previous = Objects.requireNonNull(
                    snapshots.get(index - 1)
            );
            EquitySnapshot current = Objects.requireNonNull(
                    snapshots.get(index)
            );

            if (!previous.valuationAt().isBefore(current.valuationAt())) {
                throw new IllegalArgumentException(
                        "Equity snapshots must be ordered by valuation time"
                );
            }

            if (previous.totalAssetValue().signum() <= 0) {
                throw new IllegalArgumentException(
                        "Previous asset value must be greater than zero"
                );
            }

            BigDecimal periodicReturn = current.totalAssetValue()
                    .divide(
                            previous.totalAssetValue(),
                            MathContext.DECIMAL128
                    )
                    .subtract(BigDecimal.ONE);

            periodicReturns.add(periodicReturn);
        }

        return List.copyOf(periodicReturns);
    }
}