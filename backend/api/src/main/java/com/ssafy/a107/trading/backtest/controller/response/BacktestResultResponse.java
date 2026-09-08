package com.ssafy.a107.trading.backtest.controller.response;

import com.ssafy.a107.trading.backtest.domain.BacktestResult;

import java.math.BigDecimal;
import java.util.UUID;

public record BacktestResultResponse(
        UUID backtestId,
        BigDecimal finalAssetValue,
        BigDecimal cumulativeReturn,
        BigDecimal cagr,
        BigDecimal mdd,
        BigDecimal sharpeRatio,
        BigDecimal volatility,
        BigDecimal winRate,
        int tradeCount,
        BigDecimal totalFee,
        BigDecimal benchmarkReturn
) {

    public static BacktestResultResponse from(BacktestResult backtestResult) {
        return new BacktestResultResponse(
                backtestResult.getBacktestId(),
                backtestResult.getFinalAssetValue(),
                backtestResult.getCumulativeReturn(),
                backtestResult.getCagr(),
                backtestResult.getMdd(),
                backtestResult.getSharpeRatio(),
                backtestResult.getVolatility(),
                backtestResult.getWinRate(),
                backtestResult.getTradeCount(),
                backtestResult.getTotalFee(),
                backtestResult.getBenchmarkReturn()
        );
    }
}
