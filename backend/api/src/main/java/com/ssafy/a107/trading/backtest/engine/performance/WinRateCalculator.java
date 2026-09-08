package com.ssafy.a107.trading.backtest.engine.performance;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Component
public class WinRateCalculator {

    private static final int RATE_SCALE = 8;

    public Optional<BigDecimal> calculate(
            List<BigDecimal> realizedProfitLosses
    ) {
        Objects.requireNonNull(realizedProfitLosses);

        if (realizedProfitLosses.isEmpty()) {
            return Optional.empty();
        }

        int winningTradeCount = 0;

        for (BigDecimal realizedProfitLoss : realizedProfitLosses) {
            if (realizedProfitLoss == null) {
                throw new IllegalArgumentException(
                        "Realized profit and loss must not be null"
                );
            }

            if (realizedProfitLoss.signum() > 0) {
                winningTradeCount++;
            }
        }

        BigDecimal winRate = BigDecimal.valueOf(winningTradeCount)
                .divide(
                        BigDecimal.valueOf(realizedProfitLosses.size()),
                        MathContext.DECIMAL128
                )
                .setScale(RATE_SCALE, RoundingMode.HALF_UP);

        return Optional.of(winRate);
    }
}