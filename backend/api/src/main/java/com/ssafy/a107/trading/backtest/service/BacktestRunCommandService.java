package com.ssafy.a107.trading.backtest.service;

import com.ssafy.a107.common.exception.ApiErrorCode;
import com.ssafy.a107.common.exception.ApiException;
import com.ssafy.a107.strategy.domain.Strategy;
import com.ssafy.a107.strategy.domain.dsl.RebalancePeriod;
import com.ssafy.a107.strategy.domain.dsl.Timeframe;
import com.ssafy.a107.strategy.repository.StrategyRepository;
import com.ssafy.a107.trading.backtest.domain.BacktestRun;
import com.ssafy.a107.trading.backtest.domain.ExecutionPriceRule;
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

    public BacktestRun createBacktestRun(
            UUID memberId,
            Long strategyId,
            Instant periodStart,
            Instant periodEnd,
            Timeframe timeframe,
            String quoteCurrency,
            BigDecimal initialCapital,
            BigDecimal feeRate,
            BigDecimal slippageBps,
            RebalancePeriod rebalanceInterval,
            ExecutionPriceRule executionPriceRule,
            String engineVersion,
            String marketDataVersion
    ) {
        Strategy strategy = strategyRepository
                .findByStrategyIdAndMemberMemberIdAndDeletedAtIsNull(strategyId, memberId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.STRATEGY_NOT_FOUND));

        BacktestRun backtestRun = new BacktestRun(
                strategy.getMember(),
                strategy,
                periodStart,
                periodEnd,
                timeframe,
                quoteCurrency,
                initialCapital,
                feeRate,
                slippageBps,
                rebalanceInterval,
                executionPriceRule,
                engineVersion,
                marketDataVersion,
                Instant.now()
        );
        return backtestRunRepository.save(backtestRun);
    }
}
