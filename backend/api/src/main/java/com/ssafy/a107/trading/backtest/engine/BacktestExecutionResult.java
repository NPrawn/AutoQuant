package com.ssafy.a107.trading.backtest.engine;

import com.ssafy.a107.asset.domain.Asset;
import com.ssafy.a107.trading.backtest.engine.execution.VirtualFill;
import com.ssafy.a107.trading.backtest.engine.performance.PerformanceReport;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record BacktestExecutionResult(
        PerformanceReport performanceReport,
        List<VirtualFill> fills,
        Map<UUID, Asset> assets
) {

    public BacktestExecutionResult {
        performanceReport = Objects.requireNonNull(performanceReport);
        fills = List.copyOf(fills);
        assets = Map.copyOf(assets);
    }
}
