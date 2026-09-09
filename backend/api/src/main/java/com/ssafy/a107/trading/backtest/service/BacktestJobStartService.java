package com.ssafy.a107.trading.backtest.service;

import com.ssafy.a107.trading.backtest.domain.BacktestRun;
import com.ssafy.a107.trading.backtest.domain.BacktestRunStatus;
import com.ssafy.a107.trading.backtest.messaging.BacktestJobMessage;
import com.ssafy.a107.trading.backtest.repository.BacktestRunRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

@Service
public class BacktestJobStartService {

    private final BacktestRunRepository backtestRunRepository;
    private final Clock clock;

    public BacktestJobStartService(
            BacktestRunRepository backtestRunRepository,
            Clock clock
    ) {
        this.backtestRunRepository = backtestRunRepository;
        this.clock = clock;
    }

    @Transactional
    public boolean start(BacktestJobMessage message) {
        Objects.requireNonNull(message);

        BacktestRun backtestRun = backtestRunRepository.findByBacktestIdForUpdate(message.backtestId())
                .orElseThrow(() -> new IllegalArgumentException("Backtest run not found"));

        validateMessage(message, backtestRun);

        if (backtestRun.getStatus() == BacktestRunStatus.RUNNING) {
            return true;
        }

        if (backtestRun.getStatus() != BacktestRunStatus.QUEUED) {
            return false;
        }

        backtestRun.markRunning(Instant.now(clock));
        return true;
    }

    private void validateMessage(BacktestJobMessage message, BacktestRun backtestRun) {
        boolean doesNotMatch = !backtestRun.getStrategy().getStrategyId().equals(message.strategyId())
                || !backtestRun.getMarketDataVersion().equals(message.marketDataVersion())
                || !backtestRun.getEngineVersion().equals(message.engineVersion());

        if (doesNotMatch) {
            throw new IllegalArgumentException("Backtest job message does not match backtest run");
        }
    }
}
