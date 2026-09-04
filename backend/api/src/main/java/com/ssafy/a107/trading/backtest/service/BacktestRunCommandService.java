package com.ssafy.a107.trading.backtest.service;

import com.ssafy.a107.common.exception.ApiErrorCode;
import com.ssafy.a107.common.exception.ApiException;
import com.ssafy.a107.strategy.domain.Strategy;
import com.ssafy.a107.strategy.repository.StrategyRepository;
import com.ssafy.a107.trading.backtest.domain.BacktestRun;
import com.ssafy.a107.trading.backtest.domain.ExecutionPriceRule;
import com.ssafy.a107.trading.backtest.repository.BacktestRunRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
}
