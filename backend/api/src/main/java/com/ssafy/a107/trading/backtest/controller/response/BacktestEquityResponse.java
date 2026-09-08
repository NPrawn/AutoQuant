package com.ssafy.a107.trading.backtest.controller.response;

import com.ssafy.a107.trading.backtest.domain.BacktestEquity;

import java.math.BigDecimal;
import java.time.Instant;

public record BacktestEquityResponse(
        Instant valuationAt,
        BigDecimal totalAssetValue,
        BigDecimal cashBalance,
        BigDecimal returnRate,
        BigDecimal drawdownRate
) {

    public static BacktestEquityResponse from(BacktestEquity backtestEquity) {
        return new BacktestEquityResponse(
                backtestEquity.getId().getValuationAt(),
                backtestEquity.getTotalAssetValue(),
                backtestEquity.getCashBalance(),
                backtestEquity.getReturnRate(),
                backtestEquity.getDrawdownRate()
        );
    }
}
