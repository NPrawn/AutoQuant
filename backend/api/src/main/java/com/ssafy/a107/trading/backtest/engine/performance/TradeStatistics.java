package com.ssafy.a107.trading.backtest.engine.performance;

import java.math.BigDecimal;

public record TradeStatistics(
        int tradeCount,
        BigDecimal totalFee,
        BigDecimal totalSlippage
) {

    public TradeStatistics {
        if (tradeCount < 0) {
            throw new IllegalArgumentException(
                    "Trade count must not be negative"
            );
        }

        totalFee = requireNonNegative(
                totalFee,
                "Total fee must not be negative"
        );
        totalSlippage = requireNonNegative(
                totalSlippage,
                "Total slippage must not be negative"
        );
    }

    private static BigDecimal requireNonNegative(
            BigDecimal value,
            String message
    ) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}