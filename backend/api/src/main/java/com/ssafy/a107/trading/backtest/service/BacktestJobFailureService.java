package com.ssafy.a107.trading.backtest.service;

import com.ssafy.a107.trading.backtest.domain.BacktestRun;
import com.ssafy.a107.trading.backtest.domain.BacktestRunStatus;
import com.ssafy.a107.trading.backtest.repository.BacktestRunRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

@Service
public class BacktestJobFailureService {

    private final BacktestRunRepository backtestRunRepository;

    public BacktestJobFailureService(
            BacktestRunRepository backtestRunRepository
    ) {
        this.backtestRunRepository = Objects.requireNonNull(backtestRunRepository);
    }

    @Transactional
    public boolean fail(UUID backtestId, Throwable throwable) {
        Objects.requireNonNull(backtestId);
        Objects.requireNonNull(throwable);

        BacktestRun backtestRun = backtestRunRepository
                .findByBacktestIdForUpdate(backtestId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Backtest run not found"
                ));

        if (backtestRun.getStatus() == BacktestRunStatus.COMPLETED
                || backtestRun.getStatus() == BacktestRunStatus.FAILED) {
            return false;
        }

        backtestRun.fail(failureReason(throwable));
        return true;
    }

    private String failureReason(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return throwable.getClass().getSimpleName();
        }

        return throwable.getClass().getSimpleName() + ": " + message;
    }
}
