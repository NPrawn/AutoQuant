package com.ssafy.a107.trading.backtest.engine.performance;

import com.ssafy.a107.trading.backtest.engine.execution.VirtualFill;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

@Component
public class TradeStatisticsCalculator {

    private static final int MONEY_SCALE = 8;

    public TradeStatistics calculate(List<VirtualFill> fills) {
        Objects.requireNonNull(fills);

        BigDecimal totalFee = BigDecimal.ZERO;
        BigDecimal totalSlippage = BigDecimal.ZERO;

        for (VirtualFill fill : fills) {
            if (fill == null) {
                throw new IllegalArgumentException(
                        "Virtual fill must not be null"
                );
            }

            totalFee = totalFee.add(fill.feeAmount());
            totalSlippage = totalSlippage.add(
                    fill.slippageAmount()
            );
        }

        return new TradeStatistics(
                fills.size(),
                totalFee.setScale(
                        MONEY_SCALE,
                        RoundingMode.HALF_UP
                ),
                totalSlippage.setScale(
                        MONEY_SCALE,
                        RoundingMode.HALF_UP
                )
        );
    }
}
