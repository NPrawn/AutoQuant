package com.ssafy.a107.trading.simulation.service;

import com.ssafy.a107.asset.repository.AssetRepository;
import com.ssafy.a107.realtime.domain.LiveMinuteCandle;
import com.ssafy.a107.strategy.engine.StrategyExecutionPipeline;
import com.ssafy.a107.trading.backtest.engine.BacktestRebalanceSchedule;
import com.ssafy.a107.trading.backtest.engine.execution.BacktestExecutionPolicyProperties;
import com.ssafy.a107.trading.backtest.engine.marketdata.BacktestWarmupCandleCountResolver;
import com.ssafy.a107.trading.backtest.engine.marketdata.HistoricalCandleProvider;
import com.ssafy.a107.trading.backtest.engine.portfolio.BacktestPortfolioOrderExecutionService;
import com.ssafy.a107.trading.backtest.engine.portfolio.BacktestRebalanceOrderPlanner;
import com.ssafy.a107.trading.backtest.engine.portfolio.PortfolioValuationCalculator;
import com.ssafy.a107.trading.simulation.domain.SimulationRun;
import com.ssafy.a107.trading.simulation.domain.SimulationRunStatus;
import com.ssafy.a107.trading.simulation.repository.SimulationEquityRepository;
import com.ssafy.a107.trading.simulation.repository.SimulationPositionRepository;
import com.ssafy.a107.trading.simulation.repository.SimulationResultRepository;
import com.ssafy.a107.trading.simulation.repository.SimulationRunRepository;
import com.ssafy.a107.trading.simulation.repository.SimulationTradeRepository;
import com.ssafy.a107.trading.simulation.repository.SimulationUniverseAssetRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

class SimulationCandleExecutionServiceTest {

    private final SimulationRunRepository simulationRunRepository = mock(SimulationRunRepository.class);
    private final SimulationMarketPriceCache marketPriceCache = mock(SimulationMarketPriceCache.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final SimulationExecutionFailureService failureService = mock(SimulationExecutionFailureService.class);
    private final SimulationCandleExecutionService executionService = executionService();

    @DisplayName("모의투자 시간프레임이 아닌 Candle은 실행 조회 없이 가격만 갱신한다")
    @Test
    void ignoreUnsupportedSimulationInterval() {
        LiveMinuteCandle candle = candle(1);

        executionService.handle(candle);

        then(marketPriceCache).should().update(candle);
        then(simulationRunRepository).shouldHaveNoMoreInteractions();
    }

    @DisplayName("일시적인 DB 잠금 오류는 실행을 실패 처리하지 않고 재시도한다")
    @Test
    void retryTransientTransactionFailureWithoutFailingSimulation() {
        SimulationRun simulationRun = mock(SimulationRun.class);
        UUID simulationId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        given(simulationRun.getSimulationId()).willReturn(simulationId);
        given(simulationRunRepository.findAllByStatusAndTimeframe(
                eq(SimulationRunStatus.RUNNING), any())).willReturn(List.of(simulationRun));
        given(transactionManager.getTransaction(any())).willThrow(new CannotAcquireLockException("lock"));

        executionService.handle(candle(60));

        then(transactionManager).should(org.mockito.Mockito.times(3)).getTransaction(any());
        then(failureService).shouldHaveNoInteractions();
    }

    @DisplayName("실행 대상 조회의 일시적인 DB 오류도 재시도한다")
    @Test
    void retryTransientRunningSimulationLookup() {
        given(simulationRunRepository.findAllByStatusAndTimeframe(
                eq(SimulationRunStatus.RUNNING), any())).willThrow(new CannotAcquireLockException("lock"));

        executionService.handle(candle(60));

        then(simulationRunRepository).should(org.mockito.Mockito.times(3))
                .findAllByStatusAndTimeframe(eq(SimulationRunStatus.RUNNING), any());
        then(failureService).shouldHaveNoInteractions();
    }

    private SimulationCandleExecutionService executionService() {
        return new SimulationCandleExecutionService(
                simulationRunRepository,
                mock(SimulationPositionRepository.class),
                mock(SimulationTradeRepository.class),
                mock(SimulationEquityRepository.class),
                mock(SimulationResultRepository.class),
                mock(SimulationUniverseAssetRepository.class),
                mock(AssetRepository.class),
                mock(HistoricalCandleProvider.class),
                mock(BacktestWarmupCandleCountResolver.class),
                mock(StrategyExecutionPipeline.class),
                mock(BacktestRebalanceOrderPlanner.class),
                mock(BacktestRebalanceSchedule.class),
                mock(BacktestPortfolioOrderExecutionService.class),
                mock(PortfolioValuationCalculator.class),
                mock(BacktestExecutionPolicyProperties.class),
                marketPriceCache,
                transactionManager,
                failureService
        );
    }

    private LiveMinuteCandle candle(int intervalMinutes) {
        return new LiveMinuteCandle(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "KRW-BTC", intervalMinutes, 1_800_000_000_000L,
                "100", "100", "100", "100", "1", "100", 1_800_000_000_000L, 1L
        );
    }
}
