package com.ssafy.a107.trading.backtest.service;

import com.ssafy.a107.trading.backtest.domain.BacktestRun;
import com.ssafy.a107.trading.backtest.engine.BacktestExecutionResult;
import com.ssafy.a107.trading.backtest.repository.BacktestRunRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
public class BacktestJobCompletionService {

    private final BacktestRunRepository backtestRunRepository;
    private final BacktestResultPersistenceService backtestResultPersistenceService;
    private final Clock clock;

    public BacktestJobCompletionService(
            BacktestRunRepository backtestRunRepository,
            BacktestResultPersistenceService backtestResultPersistenceService,
            Clock clock
    ) {
        this.backtestRunRepository = backtestRunRepository;
        this.backtestResultPersistenceService = backtestResultPersistenceService;
        this.clock = clock;
    }

    @Transactional
    public void complete(
            UUID backtestId,
            BacktestExecutionResult executionResult
    ) {
        Objects.requireNonNull(backtestId);
        Objects.requireNonNull(executionResult);

        BacktestRun backtestRun = backtestRunRepository.findByBacktestIdForUpdate(backtestId)
                .orElseThrow(() -> new IllegalArgumentException("Backtest run not found"));

        backtestResultPersistenceService.persist(
                backtestRun,
                executionResult.performanceReport(),
                executionResult.fills(),
                executionResult.assets(),
                executionResult.universeAssets()
        );
        backtestRun.complete(Instant.now(clock));
    }
}
