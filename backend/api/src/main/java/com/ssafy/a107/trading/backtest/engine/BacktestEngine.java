package com.ssafy.a107.trading.backtest.engine;

import com.ssafy.a107.trading.backtest.domain.BacktestRun;

public interface BacktestEngine {

    BacktestExecutionResult execute(BacktestRun backtestRun);
}
