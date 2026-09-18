package com.ssafy.a107.contest.service;

import com.ssafy.a107.asset.domain.Asset;
import com.ssafy.a107.asset.repository.AssetRepository;
import com.ssafy.a107.asset.service.CollectedAssetReadService;
import com.ssafy.a107.candle.domain.MinuteCandle;
import com.ssafy.a107.contest.domain.ContestCandle;
import com.ssafy.a107.contest.domain.ContestCandleId;
import com.ssafy.a107.contest.domain.MarketDatasetAsset;
import com.ssafy.a107.contest.repository.ContestCandleRepository;
import com.ssafy.a107.contest.repository.MarketDatasetAssetRepository;
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
import com.ssafy.a107.trading.backtest.engine.marketdata.BacktestCandleWindow;
import com.ssafy.a107.trading.backtest.engine.marketdata.BacktestCandleWindowProvider;
import com.ssafy.a107.trading.backtest.engine.marketdata.BacktestWarmupCandleCountResolver;
import com.ssafy.a107.trading.backtest.engine.marketdata.HistoricalCandleProvider;
import com.ssafy.a107.trading.backtest.engine.universe.BacktestUniverseResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/*
 * 백테스트 실행에 데이터셋 ID가 있으면 캔들과 종목이 모두 contest 테이블에서 나오는지 본다 (ADR-0014).
 */
class ContestBacktestPathIntegrationTest {

    private static final UUID DATASET_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    private static final UUID ASSET_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final Instant PERIOD_START = Instant.parse("2030-02-11T00:00:00Z");
    private static final Instant PERIOD_END = Instant.parse("2030-02-11T03:00:00Z");

    private final MarketDatasetAssetRepository datasetAssetRepository = mock(MarketDatasetAssetRepository.class);
    private final ContestCandleRepository contestCandleRepository = mock(ContestCandleRepository.class);
    private final HistoricalCandleProvider historicalCandleProvider = mock(HistoricalCandleProvider.class);
    private final CollectedAssetReadService collectedAssetReadService = mock(CollectedAssetReadService.class);
    private final AssetRepository assetRepository = mock(AssetRepository.class);
    private final JpaContestDatasetMarketData marketData =
            new JpaContestDatasetMarketData(datasetAssetRepository, contestCandleRepository);

    @DisplayName("대회 백테스트는 데이터셋 종목과 데이터셋 캔들만으로 구간을 만든다")
    @Test
    void buildCandleWindowAndUniverseFromDatasetOnly() {
        Asset asset = mock(Asset.class);
        when(asset.getAssetId()).thenReturn(ASSET_ID);
        when(datasetAssetRepository.findAllByIdDatasetIdOrderByIdAssetIdAsc(DATASET_ID))
                .thenReturn(List.of(new MarketDatasetAsset(DATASET_ID, ASSET_ID)));
        when(assetRepository.findAllById(List.of(ASSET_ID))).thenReturn(List.of(asset));
        when(contestCandleRepository
                .findAllByIdDatasetIdAndIdAssetIdInAndIdIntervalMinutesAndIdOpenTimeGreaterThanEqualAndIdOpenTimeLessThanOrderByIdAssetIdAscIdOpenTimeAsc(
                        eq(DATASET_ID), any(), anyShort(), any(), any()))
                .thenReturn(List.of(contestCandle(PERIOD_START), contestCandle(PERIOD_START.plus(Duration.ofHours(1)))));

        BacktestUniverseResolver universeResolver =
                new BacktestUniverseResolver(collectedAssetReadService, marketData, assetRepository);
        BacktestCandleWindowProvider windowProvider = new BacktestCandleWindowProvider(
                historicalCandleProvider,
                new BacktestWarmupCandleCountResolver(),
                marketData
        );
        BacktestRun backtestRun = contestBacktestRun();

        List<Asset> candidates = universeResolver.resolveCandidates(backtestRun);
        BacktestCandleWindow window = windowProvider.load(
                backtestRun,
                candidates.stream().map(Asset::getAssetId).toList()
        );

        assertThat(candidates).containsExactly(asset);
        assertThat(window.candlesByAssetId()).containsOnlyKeys(ASSET_ID);
        assertThat(window.candlesByAssetId().get(ASSET_ID))
                .extracting(candle -> candle.getId().getOpenTime())
                .containsExactly(PERIOD_START, PERIOD_START.plus(Duration.ofHours(1)));
        assertThat(window.candlesByAssetId().get(ASSET_ID))
                .extracting(MinuteCandle::getClosePrice)
                .allSatisfy(closePrice -> assertThat(closePrice).isEqualByComparingTo("110"));
        verifyNoInteractions(historicalCandleProvider, collectedAssetReadService);
    }

    private BacktestRun contestBacktestRun() {
        Strategy strategy = mock(Strategy.class);
        when(strategy.getStrategyDsl()).thenReturn(strategyDsl());

        BacktestRun backtestRun = mock(BacktestRun.class);
        when(backtestRun.isContest()).thenReturn(true);
        when(backtestRun.getContestDatasetId()).thenReturn(DATASET_ID);
        when(backtestRun.getStrategy()).thenReturn(strategy);
        when(backtestRun.getTimeframe()).thenReturn(Timeframe.ONE_HOUR);
        when(backtestRun.getPeriodStart()).thenReturn(PERIOD_START);
        when(backtestRun.getPeriodEnd()).thenReturn(PERIOD_END);
        return backtestRun;
    }

    private static ContestCandle contestCandle(Instant openTime) {
        return new ContestCandle(
                new ContestCandleId(DATASET_ID, ASSET_ID, 60, openTime),
                new BigDecimal("100"),
                new BigDecimal("120"),
                new BigDecimal("90"),
                new BigDecimal("110"),
                new BigDecimal("1.5"),
                new BigDecimal("165")
        );
    }

    private static StrategyDsl strategyDsl() {
        return new StrategyDsl(
                Timeframe.ONE_HOUR,
                new ConditionFilterNode(
                        new FactorOperand(FactorType.RSI, 14),
                        ComparisonOperator.LT,
                        new ValueOperand(BigDecimal.valueOf(30))
                ),
                new Ranking(FactorType.RETURN, 20, RankingDirection.DESC),
                new Selection(5),
                new Rebalance(RebalancePeriod.DAILY)
        );
    }
}
