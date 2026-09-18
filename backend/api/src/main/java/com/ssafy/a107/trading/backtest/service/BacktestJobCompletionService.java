package com.ssafy.a107.trading.backtest.service;

import com.ssafy.a107.trading.backtest.domain.BacktestRun;
import com.ssafy.a107.trading.backtest.domain.BacktestRunStatus;
import com.ssafy.a107.trading.backtest.engine.BacktestExecutionResult;
import com.ssafy.a107.notification.domain.NotificationType;
import com.ssafy.a107.notification.service.NotificationRequestedEvent;
import com.ssafy.a107.trading.backtest.repository.BacktestRunRepository;
import jakarta.transaction.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
public class BacktestJobCompletionService {

    private final BacktestRunRepository backtestRunRepository;
    private final BacktestResultPersistenceService backtestResultPersistenceService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final Clock clock;

    public BacktestJobCompletionService(
            BacktestRunRepository backtestRunRepository,
            BacktestResultPersistenceService backtestResultPersistenceService,
            ApplicationEventPublisher applicationEventPublisher,
            Clock clock
    ) {
        this.backtestRunRepository = backtestRunRepository;
        this.backtestResultPersistenceService = backtestResultPersistenceService;
        this.applicationEventPublisher = applicationEventPublisher;
        this.clock = clock;
    }

    @Transactional
    public boolean complete(
            UUID backtestId,
            BacktestExecutionResult executionResult
    ) {
        Objects.requireNonNull(backtestId);
        Objects.requireNonNull(executionResult);

        BacktestRun backtestRun = backtestRunRepository.findByBacktestIdForUpdate(backtestId)
                .orElseThrow(() -> new IllegalArgumentException("Backtest run not found"));

        if (backtestRun.getStatus() != BacktestRunStatus.RUNNING) {
            return false;
        }

        backtestRun.complete(Instant.now(clock));
        backtestResultPersistenceService.persist(
                backtestRun,
                executionResult.performanceReport(),
                executionResult.fills(),
                executionResult.assets(),
                executionResult.universeAssets()
        );
        /* 대회 백테스트는 제출 하나에 4벌이라 여기서 알리지 않는다. 채점이 끝나면 회차 알림 하나가 나간다. */
        if (backtestRun.isContest()) {
            return true;
        }
        applicationEventPublisher.publishEvent(new NotificationRequestedEvent(
                backtestRun.getMember().getMemberId(),
                NotificationType.BACKTEST_COMPLETED,
                BacktestNotificationMessages.completedTitle(backtestRun),
                BacktestNotificationMessages.completedBody(backtestRun),
                backtestRun.getBacktestId().toString()
        ));
        return true;
    }
}
