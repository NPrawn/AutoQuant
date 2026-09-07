package com.ssafy.a107.trading.backtest.engine.performance;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public class EquityTracker {

    private final BigDecimal initialAssetValue;
    private final CumulativeReturnCalculator cumulativeReturnCalculator;
    private final DrawdownRateCalculator drawdownRateCalculator;

    private BigDecimal peakAssetValue;
    private Instant lastValuationAt;

    public EquityTracker(
            BigDecimal initialAssetValue,
            CumulativeReturnCalculator cumulativeReturnCalculator,
            DrawdownRateCalculator drawdownRateCalculator
    ) {
        this.initialAssetValue = requirePositive(initialAssetValue);
        this.peakAssetValue = initialAssetValue;
        this.cumulativeReturnCalculator = Objects.requireNonNull(
                cumulativeReturnCalculator
        );
        this.drawdownRateCalculator = Objects.requireNonNull(
                drawdownRateCalculator
        );
    }

    public EquitySnapshot record(
            Instant valuationAt,
            BigDecimal totalAssetValue,
            BigDecimal cashBalance
    ) {
        Objects.requireNonNull(valuationAt);
        requireAfterLastValuation(valuationAt);

        BigDecimal cumulativeReturn = cumulativeReturnCalculator.calculate(
                initialAssetValue,
                totalAssetValue
        );

        BigDecimal updatedPeakAssetValue = peakAssetValue;
        if (totalAssetValue.compareTo(peakAssetValue) > 0) {
            updatedPeakAssetValue = totalAssetValue;
        }

        BigDecimal drawdownRate = drawdownRateCalculator.calculate(
                updatedPeakAssetValue,
                totalAssetValue
        );

        EquitySnapshot snapshot = new EquitySnapshot(
                valuationAt,
                totalAssetValue,
                cashBalance,
                cumulativeReturn,
                drawdownRate
        );

        peakAssetValue = updatedPeakAssetValue;
        lastValuationAt = valuationAt;
        return snapshot;
    }

    private void requireAfterLastValuation(Instant valuationAt) {
        if (lastValuationAt != null
                && !lastValuationAt.isBefore(valuationAt)) {
            throw new IllegalArgumentException(
                    "Valuation time must be after the previous valuation"
            );
        }
    }

    private BigDecimal requirePositive(BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Initial asset value must be greater than zero"
            );
        }
        return value;
    }
}