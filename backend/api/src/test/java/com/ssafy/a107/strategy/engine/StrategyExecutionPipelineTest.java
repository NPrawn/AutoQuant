package com.ssafy.a107.strategy.engine;

import com.ssafy.a107.candle.domain.MinuteCandle;
import com.ssafy.a107.candle.domain.MinuteCandleFixture;
import com.ssafy.a107.strategy.domain.dsl.ComparisonOperator;
import com.ssafy.a107.strategy.domain.dsl.ConditionFilterNode;
import com.ssafy.a107.strategy.domain.dsl.FactorOperand;
import com.ssafy.a107.strategy.domain.dsl.FactorType;
import com.ssafy.a107.strategy.domain.dsl.GroupFilterNode;
import com.ssafy.a107.strategy.domain.dsl.Logic;
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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    @DisplayName("전략에 필요한 지표를 계산할 캔들이 부족하면 실행 오류를 식별한다")
    void rejectInsufficientCandlesForRequiredFactors() {
        // given
        UUID assetId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        Map<UUID, List<MinuteCandle>> assetCandles = Map.of(assetId, candles(assetId, "100"));

        // when & then
        assertThatThrownBy(() -> pipeline.execute(
                strategyExecutionContext(strategyDsl(1), assetCandles)
        ))
                .isInstanceOf(StrategyExecutionException.class)
                .hasMessage(
                        "Insufficient candle data for asset "
                                + assetId
                                + ": [FactorOperand[factor=RSI, period=2], FactorOperand[factor=RETURN, period=2]]"
                );
    }

    @Test
    @DisplayName("백테스트는 캔들이 부족한 종목을 제외하고 계산 가능한 종목으로 실행한다")
    void excludeAssetsWithInsufficientCandlesForBacktest() {
        UUID availableAssetId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID insufficientAssetId = UUID.fromString("00000000-0000-0000-0000-000000000002");

        TargetPortfolio targetPortfolio = pipeline.executeExcludingInsufficientCandleAssets(
                strategyExecutionContext(
                        strategyDsl(1),
                        Map.of(
                                availableAssetId, candles(availableAssetId, "100", "110", "121"),
                                insufficientAssetId, candles(insufficientAssetId, "100")
                        )
                )
        );

        assertThat(targetPortfolio.assetWeights())
                .extracting(weight -> weight.assetId())
                .containsExactly(availableAssetId);
    }

    @Test
    @DisplayName("준비된 지표 시계열은 같은 시점의 기존 백테스트 판단과 같다")
    void executePreparedStrategyWithSameResultAsCurrentCandleWindow() {
        // given
        UUID assetId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        List<MinuteCandle> fullCandles = candles(assetId, "100", "110", "121", "133.1");
        StrategyDsl strategyDsl = strategyDsl(1);
        PreparedStrategyExecution prepared = pipeline.prepareForBacktest(
                strategyDsl,
                Map.of(assetId, fullCandles)
        );

        // when
        TargetPortfolio expected = pipeline.executeExcludingInsufficientCandleAssets(
                strategyExecutionContext(strategyDsl, Map.of(assetId, fullCandles.subList(0, 3)))
        );
        TargetPortfolio actual = pipeline.executePreparedForBacktest(
                prepared,
                Instant.parse("2026-01-01T03:00:00Z"),
                List.of(assetId)
        );

        // then
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    @DisplayName("백테스트 후보 전체의 캔들이 부족하면 실행 오류를 알린다")
    void rejectBacktestWhenAllCandidateAssetsHaveInsufficientCandles() {
        UUID firstAssetId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID secondAssetId = UUID.fromString("00000000-0000-0000-0000-000000000002");

        assertThatThrownBy(() -> pipeline.executeExcludingInsufficientCandleAssets(
                strategyExecutionContext(
                        strategyDsl(1),
                        Map.of(
                                firstAssetId, candles(firstAssetId, "100"),
                                secondAssetId, candles(secondAssetId, "100")
                        )
                )
        ))
                .isInstanceOf(InsufficientCandleDataException.class)
                .hasMessage("Insufficient candle data for all candidate assets");
    }

    @Test
    @DisplayName("백테스트 리밸런싱 후보가 없으면 현금만 보유한다")
    void returnCashOnlyPortfolioWhenBacktestUniverseIsEmpty() {
        TargetPortfolio targetPortfolio = pipeline.executeExcludingInsufficientCandleAssets(
                strategyExecutionContext(strategyDsl(1), Map.of())
        );

        assertThat(targetPortfolio.assetWeights()).isEmpty();
        assertThat(targetPortfolio.cashWeight()).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    @DisplayName("지원하지 않는 전략 DSL 구성은 실행 오류로 식별한다")
    void rejectUnsupportedStrategyDsl() {
        // given
        UUID assetId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        StrategyDsl unsupportedStrategyDsl = new StrategyDsl(
                Timeframe.ONE_HOUR,
                new ConditionFilterNode(
                        new FactorOperand(FactorType.RSI, 2),
                        ComparisonOperator.GT,
                        new ValueOperand(BigDecimal.ONE)
                ),
                new Ranking(null, 2, RankingDirection.DESC),
                new Selection(1),
                new Rebalance(RebalancePeriod.WEEKLY)
        );

        // when & then
        assertThatThrownBy(() -> pipeline.execute(
                strategyExecutionContext(
                        unsupportedStrategyDsl,
                        Map.of(assetId, candles(assetId, "100", "110", "121"))
                )
        ))
                .isInstanceOf(StrategyExecutionException.class)
                .hasMessage("Unsupported strategy DSL: factor operand is invalid");
    }

    @Test
    @DisplayName("Timeframe이 없는 전략 DSL은 실행 오류로 식별한다")
    void rejectStrategyDslWithoutTimeframe() {
        StrategyDsl validStrategyDsl = strategyDsl(1);

        assertUnsupportedStrategyDsl(
                new StrategyDsl(
                        null,
                        validStrategyDsl.filters(),
                        validStrategyDsl.ranking(),
                        validStrategyDsl.selection(),
                        validStrategyDsl.rebalance()
                ),
                "Unsupported strategy DSL: timeframe is required"
        );
    }

    @Test
    @DisplayName("자식이 없는 Filter Group 전략 DSL은 실행 오류로 식별한다")
    void rejectFilterGroupWithoutChildren() {
        StrategyDsl validStrategyDsl = strategyDsl(1);

        assertUnsupportedStrategyDsl(
                new StrategyDsl(
                        validStrategyDsl.timeframe(),
                        new GroupFilterNode(Logic.AND, List.of()),
                        validStrategyDsl.ranking(),
                        validStrategyDsl.selection(),
                        validStrategyDsl.rebalance()
                ),
                "Unsupported strategy DSL: filter group is invalid"
        );
    }

    @Test
    @DisplayName("null 자식이 있는 Filter Group 전략 DSL은 실행 오류로 식별한다")
    void rejectFilterGroupWithNullChild() {
        StrategyDsl validStrategyDsl = strategyDsl(1);

        assertUnsupportedStrategyDsl(
                new StrategyDsl(
                        validStrategyDsl.timeframe(),
                        new GroupFilterNode(Logic.AND, Collections.singletonList(null)),
                        validStrategyDsl.ranking(),
                        validStrategyDsl.selection(),
                        validStrategyDsl.rebalance()
                ),
                "Unsupported strategy DSL: filter node is required"
        );
    }

    @Test
    @DisplayName("null Operand가 있는 Filter 전략 DSL은 실행 오류로 식별한다")
    void rejectFilterWithNullOperand() {
        StrategyDsl validStrategyDsl = strategyDsl(1);

        assertUnsupportedStrategyDsl(
                new StrategyDsl(
                        validStrategyDsl.timeframe(),
                        new ConditionFilterNode(null, ComparisonOperator.GT, new ValueOperand(BigDecimal.ONE)),
                        validStrategyDsl.ranking(),
                        validStrategyDsl.selection(),
                        validStrategyDsl.rebalance()
                ),
                "Unsupported strategy DSL: filter operand is required"
        );
    }

    @Test
    @DisplayName("0 이하 Top N 전략 DSL은 실행 오류로 식별한다")
    void rejectStrategyDslWithNonPositiveTopN() {
        StrategyDsl validStrategyDsl = strategyDsl(1);

        assertUnsupportedStrategyDsl(
                new StrategyDsl(
                        validStrategyDsl.timeframe(),
                        validStrategyDsl.filters(),
                        validStrategyDsl.ranking(),
                        new Selection(0),
                        validStrategyDsl.rebalance()
                ),
                "Unsupported strategy DSL: selection topN must be positive"
        );
    }

    @Test
    @DisplayName("리밸런싱 주기가 없는 전략 DSL은 실행 오류로 식별한다")
    void rejectStrategyDslWithoutRebalancePeriod() {
        StrategyDsl validStrategyDsl = strategyDsl(1);

        assertUnsupportedStrategyDsl(
                new StrategyDsl(
                        validStrategyDsl.timeframe(),
                        validStrategyDsl.filters(),
                        validStrategyDsl.ranking(),
                        validStrategyDsl.selection(),
                        new Rebalance(null)
                ),
                "Unsupported strategy DSL: rebalance period is required"
        );
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

    private void assertUnsupportedStrategyDsl(StrategyDsl strategyDsl, String expectedMessage) {
        UUID assetId = UUID.fromString("00000000-0000-0000-0000-000000000001");

        assertThatThrownBy(() -> pipeline.execute(
                strategyExecutionContext(
                        strategyDsl,
                        Map.of(assetId, candles(assetId, "100", "110", "121"))
                )
        ))
                .isInstanceOf(StrategyExecutionException.class)
                .hasMessage(expectedMessage);
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
