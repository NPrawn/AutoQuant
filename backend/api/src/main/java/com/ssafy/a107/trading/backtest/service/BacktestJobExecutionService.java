package com.ssafy.a107.trading.backtest.service;

import com.ssafy.a107.trading.backtest.domain.BacktestRun;
import com.ssafy.a107.trading.backtest.engine.BacktestEngine;
import com.ssafy.a107.trading.backtest.engine.BacktestExecutionResult;
import com.ssafy.a107.trading.backtest.messaging.BacktestJobMessage;
import com.ssafy.a107.trading.backtest.repository.BacktestRunRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@Profile("backtest-worker")
public class BacktestJobExecutionService {

    private final BacktestJobStartService backtestJobStartService;
    private final BacktestRunRepository backtestRunRepository;
    private final BacktestEngine backtestEngine;
    private final BacktestJobCompletionService backtestJobCompletionService;

    public BacktestJobExecutionService(
            BacktestJobStartService backtestJobStartService,
            BacktestRunRepository backtestRunRepository,
            BacktestEngine backtestEngine,
            BacktestJobCompletionService backtestJobCompletionService
    ) {
        this.backtestJobStartService = backtestJobStartService;
        this.backtestRunRepository = backtestRunRepository;
        this.backtestEngine = backtestEngine;
        this.backtestJobCompletionService = backtestJobCompletionService;
    }

    public boolean execute(BacktestJobMessage message) {
        Objects.requireNonNull(message);

        if (!backtestJobStartService.start(message)) {
            return false;
        }

        BacktestRun backtestRun = backtestRunRepository.findByBacktestId(message.backtestId())
                .orElseThrow(() -> new IllegalArgumentException("Backtest run not found"));

        BacktestExecutionResult executionResult = backtestEngine.execute(backtestRun);
        backtestJobCompletionService.complete(
                message.backtestId(),
                executionResult
        );

        return true;
    }
}
