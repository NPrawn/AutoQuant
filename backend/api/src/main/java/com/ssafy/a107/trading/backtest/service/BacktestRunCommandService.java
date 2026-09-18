package com.ssafy.a107.trading.backtest.service;

import com.ssafy.a107.common.exception.ApiErrorCode;
import com.ssafy.a107.common.exception.ApiException;
import com.ssafy.a107.strategy.domain.Strategy;
import com.ssafy.a107.strategy.repository.StrategyRepository;
import com.ssafy.a107.trading.backtest.domain.BacktestRun;
import com.ssafy.a107.trading.backtest.domain.ExecutionPriceRule;
import com.ssafy.a107.trading.backtest.messaging.BacktestJobMessage;
import com.ssafy.a107.trading.backtest.messaging.BacktestJobOutboxService;
import com.ssafy.a107.trading.backtest.repository.BacktestRunRepository;
import com.ssafy.a107.trading.backtest.messaging.BacktestJobQueuedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
@Transactional
public class BacktestRunCommandService {

    private final BacktestRunRepository backtestRunRepository;
    private final StrategyRepository strategyRepository;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final BacktestJobOutboxService backtestJobOutboxService;

    public BacktestRunCommandService(
            BacktestRunRepository backtestRunRepository,
            StrategyRepository strategyRepository,
            ApplicationEventPublisher applicationEventPublisher,
            BacktestJobOutboxService backtestJobOutboxService
    ) {
        this.backtestRunRepository = backtestRunRepository;
        this.strategyRepository = strategyRepository;
        this.applicationEventPublisher = applicationEventPublisher;
        this.backtestJobOutboxService = backtestJobOutboxService;
    }

    public BacktestRun createBacktestRun(UUID memberId, CreateBacktestRunCommand command) {
        validate(command);

        Strategy strategy = strategyRepository
                .findByStrategyIdAndMemberMemberIdAndDeletedAtIsNull(command.strategyId(), memberId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.STRATEGY_NOT_FOUND));

        BacktestRun backtestRun = new BacktestRun(
                strategy.getMember(),
                strategy,
                command.periodStart(),
                command.periodEnd(),
                strategy.getStrategyDsl().timeframe(),
                command.quoteCurrency(),
                command.initialCapital(),
                command.feeRate(),
                command.slippageBps(),
                strategy.getStrategyDsl().rebalance().period(),
                command.executionPriceRule(),
                command.engineVersion(),
                command.marketDataVersion(),
                Instant.now()
        );

        BacktestRun savedBacktestRun = backtestRunRepository.save(backtestRun);

        BacktestJobMessage message = BacktestJobMessage.from(
                UUID.randomUUID(),
                savedBacktestRun
        );
        backtestJobOutboxService.enqueue(message);
        applicationEventPublisher.publishEvent(
                new BacktestJobQueuedEvent(message)
        );

        return savedBacktestRun;
    }

    /*
     * 채점이 부르는 경로다. 제출 뒤 전략을 삭제해도 채점하므로 소유자와 삭제 여부로 거르지 않는다 (대회 기능 설계 3절).
     */
    public BacktestRun createContestBacktestRun(CreateContestBacktestRunCommand command) {
        validate(command);

        Strategy strategy = strategyRepository.findById(command.strategyId())
                .orElseThrow(() -> new ApiException(ApiErrorCode.STRATEGY_NOT_FOUND));

        BacktestRun backtestRun = new BacktestRun(
                strategy.getMember(),
                strategy,
                command.periodStart(),
                command.periodEnd(),
                strategy.getStrategyDsl().timeframe(),
                "KRW",
                command.initialCapital(),
                command.feeRate(),
                command.slippageBps(),
                strategy.getStrategyDsl().rebalance().period(),
                ExecutionPriceRule.NEXT_CANDLE_OPEN,
                command.engineVersion(),
                command.marketDataVersion(),
                Instant.now(),
                command.contestDatasetId()
        );

        BacktestRun savedBacktestRun = backtestRunRepository.save(backtestRun);

        BacktestJobMessage message = BacktestJobMessage.from(
                UUID.randomUUID(),
                savedBacktestRun
        );
        backtestJobOutboxService.enqueue(message);
        applicationEventPublisher.publishEvent(
                new BacktestJobQueuedEvent(message)
        );

        return savedBacktestRun;
    }

    private void validate(CreateContestBacktestRunCommand command) {
        if (command == null || command.periodStart() == null || command.periodEnd() == null
                || !command.periodStart().isBefore(command.periodEnd())) {
            throw new ApiException(ApiErrorCode.BACKTEST_INVALID_PERIOD);
        }
        if (command.strategyId() == null || command.strategyId() <= 0
                || command.contestDatasetId() == null
                || isInvalidVersion(command.engineVersion())
                || isInvalidVersion(command.marketDataVersion())) {
            throw new ApiException(ApiErrorCode.BACKTEST_INVALID_CONFIGURATION);
        }
    }

    private void validate(CreateBacktestRunCommand command) {
        if (command == null || command.periodStart() == null || command.periodEnd() == null
                || !command.periodStart().isBefore(command.periodEnd())) {
            throw new ApiException(ApiErrorCode.BACKTEST_INVALID_PERIOD);
        }
        if (command.strategyId() == null || command.strategyId() <= 0
                || command.executionPriceRule() == null
                || isInvalidVersion(command.engineVersion())
                || isInvalidVersion(command.marketDataVersion())) {
            throw new ApiException(ApiErrorCode.BACKTEST_INVALID_CONFIGURATION);
        }
        if (command.quoteCurrency() == null || !"KRW".equalsIgnoreCase(command.quoteCurrency().trim())) {
            throw new ApiException(ApiErrorCode.BACKTEST_UNSUPPORTED_QUOTE_CURRENCY);
        }
        if (command.initialCapital() == null || command.initialCapital().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(ApiErrorCode.BACKTEST_INVALID_INITIAL_CAPITAL);
        }
        if (command.feeRate() == null || command.slippageBps() == null
                || command.feeRate().signum() < 0
                || command.feeRate().compareTo(BigDecimal.ONE) >= 0
                || command.slippageBps().signum() < 0) {
            throw new ApiException(ApiErrorCode.BACKTEST_INVALID_COST);
        }
    }

    private boolean isInvalidVersion(String version) {
        return version == null || version.isBlank() || version.trim().length() > 50;
    }
}
