package com.ssafy.a107.strategy.engine;

import com.ssafy.a107.candle.domain.MinuteCandle;
import com.ssafy.a107.candle.domain.MinuteCandleFixture;
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
import com.ssafy.a107.strategy.engine.factor.Ta4jFactorCalculator;
import com.ssafy.a107.strategy.engine.filter.FilterEvaluator;
import com.ssafy.a107.strategy.engine.portfolio.TargetPortfolio;
import com.ssafy.a107.strategy.engine.portfolio.TargetPortfolioEvaluator;
import com.ssafy.a107.strategy.engine.ranking.RankingEvaluator;
import com.ssafy.a107.strategy.engine.selection.SelectionEvaluator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class StrategyExecutionPipelineTest {

    private final StrategyExecutionPipeline pipeline = new StrategyExecutionPipeline(
            new Ta4jFactorCalculator(),
            new FilterEvaluator(),
            new RankingEvaluator(),
            new SelectionEvaluator(),
            new TargetPortfolioEvaluator()
    );

    @Test
    @DisplayName("Factor 계산부터 목표 포트폴리오 구성까지 전략 실행 흐름을 수행한다")
    void executeStrategyPipeline() {
        // given
        UUID risingAssetId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID fallingAssetId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        Map<UUID, List<MinuteCandle>> assetCandles = new LinkedHashMap<>();
        assetCandles.put(risingAssetId, candles(risingAssetId, "100", "110", "121"));
        assetCandles.put(fallingAssetId, candles(fallingAssetId, "100", "90", "81"));

        // when
        TargetPortfolio targetPortfolio = pipeline.execute(
                strategyExecutionContext(strategyDsl(1), assetCandles)
        );

        // then
        assertThat(targetPortfolio.assetWeights()).hasSize(1);
        assertThat(targetPortfolio.assetWeights().getFirst().assetId()).isEqualTo(risingAssetId);
        assertThat(targetPortfolio.assetWeights().getFirst().targetWeight()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(targetPortfolio.cashWeight()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Filter를 통과한 종목이 없으면 현금 100퍼센트 목표 포트폴리오를 반환한다")
    void returnCashOnlyPortfolioWhenNoAssetPassesFilter() {
        // given
        UUID assetId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        Map<UUID, List<MinuteCandle>> assetCandles = Map.of(assetId, candles(assetId, "100", "110", "121"));

        // when
        TargetPortfolio targetPortfolio = pipeline.execute(
                strategyExecutionContext(strategyDsl(1_000), assetCandles)
        );

        // then
        assertThat(targetPortfolio.assetWeights()).isEmpty();
        assertThat(targetPortfolio.cashWeight()).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    @DisplayName("전략 Timeframe과 다른 간격의 캔들은 실행하지 않는다")
    void rejectCandlesWithDifferentTimeframe() {
        // given
        UUID assetId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        List<MinuteCandle> candles = List.of(
                MinuteCandleFixture.minuteCandle(
                        assetId,
                        1,
                        Instant.parse("2026-01-01T00:00:00Z"),
                        BigDecimal.ONE,
                        BigDecimal.ONE
                )
        );

        // when & then
        assertThatIllegalArgumentException()
                .isThrownBy(() -> pipeline.execute(
                        strategyExecutionContext(strategyDsl(1), Map.of(assetId, candles))
                ))
                .withMessage("Candle interval must match strategy timeframe");
    }

    private StrategyExecutionContext strategyExecutionContext(
            StrategyDsl strategyDsl,
            Map<UUID, List<MinuteCandle>> assetCandles
    ) {
        return new StrategyExecutionContext(
                strategyDsl,
                Instant.parse("2026-01-01T03:00:00Z"),
                assetCandles
        );
    }

    private StrategyDsl strategyDsl(int minimumRsi) {
        return new StrategyDsl(
                Timeframe.ONE_HOUR,
                new ConditionFilterNode(
                        new FactorOperand(FactorType.RSI, 2),
                        ComparisonOperator.GT,
                        new ValueOperand(BigDecimal.valueOf(minimumRsi))
                ),
                new Ranking(FactorType.RETURN, 2, RankingDirection.DESC),
                new Selection(1),
                new Rebalance(RebalancePeriod.WEEKLY)
        );
    }

    private List<MinuteCandle> candles(UUID assetId, String... closes) {
        Instant firstOpenTime = Instant.parse("2026-01-01T00:00:00Z");
        List<MinuteCandle> candles = new ArrayList<>();
        for (int index = 0; index < closes.length; index++) {
            candles.add(MinuteCandleFixture.minuteCandle(
                    assetId,
                    60,
                    firstOpenTime.plusSeconds(index * 3_600L),
                    new BigDecimal(closes[index]),
                    BigDecimal.ONE
            ));
        }
        return candles;
    }
}
