package com.ssafy.a107.trading.backtest.service;

import com.ssafy.a107.common.exception.ApiErrorCode;
import com.ssafy.a107.common.exception.ApiException;
import com.ssafy.a107.strategy.domain.Strategy;
import com.ssafy.a107.strategy.repository.StrategyRepository;
import com.ssafy.a107.trading.backtest.domain.BacktestRun;
import com.ssafy.a107.trading.backtest.repository.BacktestRunRepository;
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

    public BacktestRunCommandService(
            BacktestRunRepository backtestRunRepository,
            StrategyRepository strategyRepository
    ) {
        this.backtestRunRepository = backtestRunRepository;
        this.strategyRepository = strategyRepository;
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
        return backtestRunRepository.save(backtestRun);
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
                || command.feeRate().signum() < 0 || command.slippageBps().signum() < 0) {
            throw new ApiException(ApiErrorCode.BACKTEST_INVALID_COST);
        }
    }

    private boolean isInvalidVersion(String version) {
        return version == null || version.isBlank() || version.trim().length() > 50;
    }
}
