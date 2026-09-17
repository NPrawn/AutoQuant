package com.ssafy.a107.trading.backtest.service;

import com.ssafy.a107.trading.backtest.domain.BacktestRun;
import com.ssafy.a107.trading.backtest.domain.BacktestFailureCode;
import com.ssafy.a107.trading.backtest.domain.BacktestRunStatus;
import com.ssafy.a107.strategy.engine.InsufficientCandleDataException;
import com.ssafy.a107.trading.backtest.engine.BacktestExecutionException;
import com.ssafy.a107.notification.domain.NotificationType;
import com.ssafy.a107.notification.service.NotificationRequestedEvent;
import com.ssafy.a107.trading.backtest.repository.BacktestRunRepository;
import jakarta.transaction.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

@Service
public class BacktestJobFailureService {

    private final BacktestRunRepository backtestRunRepository;
    private final ApplicationEventPublisher applicationEventPublisher;

    public BacktestJobFailureService(
            BacktestRunRepository backtestRunRepository,
            ApplicationEventPublisher applicationEventPublisher
    ) {
        this.backtestRunRepository = Objects.requireNonNull(backtestRunRepository);
        this.applicationEventPublisher = Objects.requireNonNull(applicationEventPublisher);
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

        BacktestFailureCode failureCode = failureCode(throwable);
        backtestRun.fail(failureCode, failureReason(throwable));
        /* 대회 백테스트가 실패하면 그 제출은 실격이다. 채점 알림 하나로 알린다. */
        if (backtestRun.isContest()) {
            return true;
        }
        applicationEventPublisher.publishEvent(new NotificationRequestedEvent(
                backtestRun.getMember().getMemberId(),
                NotificationType.BACKTEST_FAILED,
                BacktestNotificationMessages.failedTitle(backtestRun),
                BacktestNotificationMessages.failedBody(failureCode),
                backtestRun.getBacktestId().toString()
        ));
        return true;
    }

    private BacktestFailureCode failureCode(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof BacktestExecutionException backtestExecutionException) {
                return backtestExecutionException.getFailureCode();
            }
            if (current instanceof InsufficientCandleDataException) {
                return BacktestFailureCode.INSUFFICIENT_CANDLES;
            }
            current = current.getCause();
        }
        return BacktestFailureCode.ENGINE_ERROR;
    }

    private String failureReason(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return throwable.getClass().getSimpleName();
        }

        return throwable.getClass().getSimpleName() + ": " + message;
    }
}
