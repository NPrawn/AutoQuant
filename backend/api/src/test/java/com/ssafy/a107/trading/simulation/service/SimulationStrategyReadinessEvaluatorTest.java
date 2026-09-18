package com.ssafy.a107.trading.simulation.service;

import com.ssafy.a107.asset.domain.Asset;
import com.ssafy.a107.asset.domain.Exchange;
import com.ssafy.a107.candle.domain.MinuteCandle;
import com.ssafy.a107.candle.domain.MinuteCandleId;
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
import com.ssafy.a107.trading.backtest.engine.marketdata.BacktestWarmupCandleCountResolver;
import com.ssafy.a107.trading.backtest.engine.marketdata.HistoricalCandleProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class SimulationStrategyReadinessEvaluatorTest {

    private static final Instant EXECUTION_AT = Instant.parse("2026-09-18T00:00:00Z");

    @Mock
    private HistoricalCandleProvider historicalCandleProvider;

    private final Map<UUID, List<MinuteCandle>> candlesByAssetId = new HashMap<>();
    private SimulationStrategyReadinessEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new SimulationStrategyReadinessEvaluator(
                historicalCandleProvider, new BacktestWarmupCandleCountResolver()
        );
        given(historicalCandleProvider.load(any(), any(), any(), any())).willAnswer(invocation -> {
            Collection<UUID> assetIds = invocation.getArgument(0);
            Instant fromInclusive = invocation.getArgument(2);
            Instant toExclusive = invocation.getArgument(3);
            Map<UUID, List<MinuteCandle>> loaded = new HashMap<>();
            for (UUID assetId : assetIds) {
                loaded.put(assetId, candlesByAssetId.getOrDefault(assetId, List.of()).stream()
                        .filter(candle -> !candle.getId().getOpenTime().isBefore(fromInclusive)
                                && candle.getId().getOpenTime().isBefore(toExclusive))
                        .toList());
            }
            return loaded;
        });
    }

    @Test
    @DisplayName("최신 확정 봉을 숨기면 즉시 대기 상태를 재현하고 원래 데이터에서는 준비 완료를 보인다")
    void simulateMissingLatestCompletedCandle() {
        Asset asset = asset("KRW-READY", 21);

        SimulationStrategyReadinessEvaluator.Evaluation waiting = evaluate(List.of(asset), true);
        SimulationStrategyReadinessEvaluator.Evaluation ready = evaluate(List.of(asset), false);

        assertThat(waiting.state()).isEqualTo(SimulationStrategyReadinessEvaluator.State.WAITING_FOR_CANDLES);
        assertThat(waiting.assets()).singleElement().extracting("state")
                .isEqualTo(SimulationStrategyReadinessEvaluator.State.WAITING_FOR_LATEST_CANDLE);
        assertThat(ready.state()).isEqualTo(SimulationStrategyReadinessEvaluator.State.READY);
        assertThat(ready.assets()).singleElement().extracting("state")
                .isEqualTo(SimulationStrategyReadinessEvaluator.State.READY);
    }

    @Test
    @DisplayName("과거 이력이 끊긴 미보유 종목은 제외하고 준비된 종목으로 판단한다")
    void excludeStaleUnheldAsset() {
        Asset readyAsset = asset("KRW-READY", 21);
        Asset staleAsset = asset("KRW-STALE", 18);

        SimulationStrategyReadinessEvaluator.Evaluation result = evaluate(List.of(readyAsset, staleAsset), false);

        assertThat(result.state()).isEqualTo(SimulationStrategyReadinessEvaluator.State.READY);
        assertThat(result.readyCandlesByAssetId()).containsOnlyKeys(readyAsset.getAssetId());
        assertThat(result.assets()).filteredOn(asset -> asset.assetId().equals(staleAsset.getAssetId()))
                .singleElement().extracting("state")
                .isEqualTo(SimulationStrategyReadinessEvaluator.State.EXCLUDED_INSUFFICIENT_HISTORY);
    }

    private SimulationStrategyReadinessEvaluator.Evaluation evaluate(
            List<Asset> universe, boolean hideLatestCompletedCandle
    ) {
        return evaluator.evaluate(strategyDsl(), Timeframe.ONE_DAY, universe, Set.of(), EXECUTION_AT,
                hideLatestCompletedCandle);
    }

    private Asset asset(String marketCode, int candleCount) {
        Asset asset = new Asset(Exchange.UPBIT, marketCode, marketCode.substring(4), "KRW", "테스트", "Test", null);
        UUID assetId = UUID.randomUUID();
        ReflectionTestUtils.setField(asset, "assetId", assetId);
        List<MinuteCandle> candles = new ArrayList<>();
        for (int index = 0; index < candleCount; index++) {
            Instant openTime = EXECUTION_AT.minus(Duration.ofDays(21 - index));
            BigDecimal price = BigDecimal.valueOf(100 + index);
            candles.add(MinuteCandle.detached(new MinuteCandleId(assetId, 1440, openTime),
                    price, price, price, price, BigDecimal.ONE, price,
                    openTime.plus(Duration.ofDays(1)).minusMillis(1)));
        }
        candlesByAssetId.put(assetId, candles);
        return asset;
    }

    private StrategyDsl strategyDsl() {
        return new StrategyDsl(Timeframe.ONE_DAY,
                new ConditionFilterNode(new FactorOperand(FactorType.VOLATILITY, 20), ComparisonOperator.LT,
                        new ValueOperand(new BigDecimal("100"))),
                new Ranking(FactorType.RETURN, 20, RankingDirection.DESC), new Selection(1),
                new Rebalance(RebalancePeriod.DAILY));
    }
}
