package com.ssafy.a107.trading.backtest.service;

import com.ssafy.a107.trading.backtest.domain.BacktestRun;
import com.ssafy.a107.trading.backtest.engine.BacktestEngine;
import com.ssafy.a107.trading.backtest.engine.BacktestExecutionResult;
import com.ssafy.a107.trading.backtest.messaging.BacktestJobMessage;
import com.ssafy.a107.trading.backtest.repository.BacktestRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@Profile("backtest-worker")
public class BacktestJobExecutionService {

    private static final Logger log = LoggerFactory.getLogger(BacktestJobExecutionService.class);

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
        return execute(message, false);
    }

    public boolean execute(BacktestJobMessage message, boolean redelivered) {
        Objects.requireNonNull(message);

        if (!backtestJobStartService.start(message, redelivered)) {
            return false;
        }

        long runLoadStartedAt = System.nanoTime();
        BacktestRun backtestRun = backtestRunRepository.findByBacktestId(message.backtestId())
                .orElseThrow(() -> new IllegalArgumentException("Backtest run not found"));
        long runLoadElapsedMillis = elapsedMillis(runLoadStartedAt);

        long engineStartedAt = System.nanoTime();
        BacktestExecutionResult executionResult = backtestEngine.execute(backtestRun);
        long engineElapsedMillis = elapsedMillis(engineStartedAt);

        long completionStartedAt = System.nanoTime();
        boolean completed = backtestJobCompletionService.complete(
                message.backtestId(),
                executionResult
        );
        long completionElapsedMillis = elapsedMillis(completionStartedAt);

        log.info(
                "Backtest worker timing backtestId={} completed={} runLoadMs={} engineMs={} completionMs={} fills={} equitySnapshots={} universeAssets={}",
                message.backtestId(),
                completed,
                runLoadElapsedMillis,
                engineElapsedMillis,
                completionElapsedMillis,
                executionResult.fills().size(),
                executionResult.performanceReport().equitySnapshots().size(),
                executionResult.universeAssets().size()
        );
        return completed;
    }

    private long elapsedMillis(long startedAt) {
        return java.time.Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }
}
