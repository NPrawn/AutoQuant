package com.ssafy.a107.trading.backtest.engine.marketdata;

import com.ssafy.a107.strategy.domain.Strategy;
import com.ssafy.a107.strategy.domain.dsl.ComparisonOperator;
import com.ssafy.a107.strategy.domain.dsl.ConditionFilterNode;
import com.ssafy.a107.strategy.domain.dsl.FactorOperand;
import com.ssafy.a107.strategy.domain.dsl.FactorType;
import com.ssafy.a107.strategy.domain.dsl.Ranking;
import com.ssafy.a107.strategy.domain.dsl.RankingDirection;
import com.ssafy.a107.strategy.domain.dsl.Rebalance;
import com.ssafy.a107.strategy.domain.dsl.RebalancePeriod;
import com.ssafy.a107.strategy.domain.dsl.Selection;
import com.ssafy.a107.strategy.domain.dsl.StrategyDsl;
import com.ssafy.a107.strategy.domain.dsl.Timeframe;
import com.ssafy.a107.strategy.domain.dsl.ValueOperand;
import com.ssafy.a107.trading.backtest.domain.BacktestRun;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BacktestCandleWindowProviderTest {

    private static final UUID ASSET_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID DATASET_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    private static final Instant PERIOD_START = Instant.parse("2026-01-02T00:00:00Z");
    private static final Instant PERIOD_END = Instant.parse("2026-01-03T00:00:00Z");

    @DisplayName("Warm-up 캔들 수만큼 조회 시작 시각을 앞당겨 캔들 구간을 생성한다")
    @Test
    void loadCandleWindowIncludingWarmupPeriod() {
        HistoricalCandleProvider historicalCandleProvider = mock(HistoricalCandleProvider.class);
        BacktestWarmupCandleCountResolver warmupCandleCountResolver = mock(BacktestWarmupCandleCountResolver.class);
        BacktestCandleWindowProvider provider = new BacktestCandleWindowProvider(
                historicalCandleProvider,
                warmupCandleCountResolver,
                mock(ContestDatasetMarketData.class)
        );
        BacktestRun backtestRun = mock(BacktestRun.class);
        Strategy strategy = mock(Strategy.class);
        StrategyDsl strategyDsl = strategyDsl();
        Instant expectedLoadStart = Instant.parse("2026-01-01T21:00:00Z");
        Instant expectedLoadEnd = Instant.parse("2026-01-03T01:00:00Z");

        when(backtestRun.getStrategy()).thenReturn(strategy);
        when(strategy.getStrategyDsl()).thenReturn(strategyDsl);
        when(backtestRun.getTimeframe()).thenReturn(Timeframe.ONE_HOUR);
        when(backtestRun.getPeriodStart()).thenReturn(PERIOD_START);
        when(backtestRun.getPeriodEnd()).thenReturn(PERIOD_END);
        when(warmupCandleCountResolver.resolve(strategyDsl)).thenReturn(3);
        when(historicalCandleProvider.load(
                List.of(ASSET_ID),
                Timeframe.ONE_HOUR,
                expectedLoadStart,
                expectedLoadEnd
        )).thenReturn(Map.of(ASSET_ID, List.of()));

        BacktestCandleWindow window = provider.load(backtestRun, List.of(ASSET_ID));

        assertThat(window.timeframe()).isEqualTo(Timeframe.ONE_HOUR);
        assertThat(window.evaluationStart()).isEqualTo(PERIOD_START);
        assertThat(window.evaluationEnd()).isEqualTo(PERIOD_END);
        assertThat(window.candlesByAssetId()).containsKey(ASSET_ID);
        verify(warmupCandleCountResolver).resolve(strategyDsl);
        verify(historicalCandleProvider).load(
                List.of(ASSET_ID),
                Timeframe.ONE_HOUR,
                expectedLoadStart,
                expectedLoadEnd
        );
    }

    @DisplayName("대회 백테스트는 실제 캔들 대신 데이터셋 캔들을 읽는다")
    @Test
    void loadContestDatasetCandles() {
        HistoricalCandleProvider historicalCandleProvider = mock(HistoricalCandleProvider.class);
        BacktestWarmupCandleCountResolver warmupCandleCountResolver = mock(BacktestWarmupCandleCountResolver.class);
        ContestDatasetMarketData contestDatasetMarketData = mock(ContestDatasetMarketData.class);
        BacktestCandleWindowProvider provider = new BacktestCandleWindowProvider(
                historicalCandleProvider,
                warmupCandleCountResolver,
                contestDatasetMarketData
        );
        BacktestRun backtestRun = mock(BacktestRun.class);
        Strategy strategy = mock(Strategy.class);
        StrategyDsl strategyDsl = strategyDsl();
        Instant expectedLoadStart = Instant.parse("2026-01-01T21:00:00Z");
        Instant expectedLoadEnd = Instant.parse("2026-01-03T01:00:00Z");

        when(backtestRun.getStrategy()).thenReturn(strategy);
        when(strategy.getStrategyDsl()).thenReturn(strategyDsl);
        when(backtestRun.getTimeframe()).thenReturn(Timeframe.ONE_HOUR);
        when(backtestRun.getPeriodStart()).thenReturn(PERIOD_START);
        when(backtestRun.getPeriodEnd()).thenReturn(PERIOD_END);
        when(backtestRun.isContest()).thenReturn(true);
        when(backtestRun.getContestDatasetId()).thenReturn(DATASET_ID);
        when(warmupCandleCountResolver.resolve(strategyDsl)).thenReturn(3);
        when(contestDatasetMarketData.loadCandles(
                DATASET_ID,
                List.of(ASSET_ID),
                Timeframe.ONE_HOUR,
                expectedLoadStart,
                expectedLoadEnd
        )).thenReturn(Map.of(ASSET_ID, List.of()));

        BacktestCandleWindow window = provider.load(backtestRun, List.of(ASSET_ID));

        assertThat(window.candlesByAssetId()).containsKey(ASSET_ID);
        verify(contestDatasetMarketData).loadCandles(
                DATASET_ID,
                List.of(ASSET_ID),
                Timeframe.ONE_HOUR,
                expectedLoadStart,
                expectedLoadEnd
        );
        verifyNoInteractions(historicalCandleProvider);
    }

    private StrategyDsl strategyDsl() {
        return new StrategyDsl(
                Timeframe.ONE_HOUR,
                new ConditionFilterNode(
                        new FactorOperand(FactorType.RSI, 14),
                        ComparisonOperator.GT,
                        new ValueOperand(BigDecimal.valueOf(30))
                ),
                new Ranking(FactorType.RETURN, 20, RankingDirection.DESC),
                new Selection(5),
                new Rebalance(RebalancePeriod.WEEKLY)
        );
    }
}
