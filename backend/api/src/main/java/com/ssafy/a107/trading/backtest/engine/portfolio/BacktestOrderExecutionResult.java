package com.ssafy.a107.trading.backtest.engine.portfolio;

import com.ssafy.a107.trading.backtest.engine.execution.VirtualFill;

import java.util.List;
import java.util.Objects;

public record BacktestOrderExecutionResult(
        List<VirtualFill> fills,
        List<RealizedTradeResult> realizedTrades
) {

    public BacktestOrderExecutionResult {
        fills = List.copyOf(Objects.requireNonNull(fills));
        realizedTrades = List.copyOf(Objects.requireNonNull(realizedTrades));
    }
}
