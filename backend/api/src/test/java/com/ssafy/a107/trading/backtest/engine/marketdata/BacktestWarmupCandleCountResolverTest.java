package com.ssafy.a107.trading.backtest.engine.marketdata;

import com.ssafy.a107.strategy.domain.dsl.ComparisonOperator;
import com.ssafy.a107.strategy.domain.dsl.ConditionFilterNode;
import com.ssafy.a107.strategy.domain.dsl.FactorOperand;
import com.ssafy.a107.strategy.domain.dsl.FactorType;
import com.ssafy.a107.strategy.domain.dsl.FilterNode;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class BacktestWarmupCandleCountResolverTest {

    private final BacktestWarmupCandleCountResolver resolver = new BacktestWarmupCandleCountResolver();

    @DisplayName("중첩 Filter 조건의 최대 Factor 기간에 한 캔들을 더해 Warm-up 수를 계산한다")
    @Test
    void resolveWarmupCountFromNestedFilterPeriods() {
        StrategyDsl strategyDsl = strategyDsl(
                new GroupFilterNode(Logic.AND, List.of(
                        condition(14),
                        new GroupFilterNode(Logic.OR, List.of(
                                condition(60),
                                condition(30)
                        ))
                )),
                20
        );

        int warmupCandleCount = resolver.resolve(strategyDsl);

        assertThat(warmupCandleCount).isEqualTo(61);
    }

    @DisplayName("Ranking 기간이 Filter의 모든 Factor 기간보다 크면 Ranking 기간을 사용한다")
    @Test
    void resolveWarmupCountFromRankingPeriod() {
        StrategyDsl strategyDsl = strategyDsl(condition(14), 90);

        int warmupCandleCount = resolver.resolve(strategyDsl);

        assertThat(warmupCandleCount).isEqualTo(91);
    }

    @DisplayName("양수가 아닌 Ranking 기간은 Warm-up 계산에서 거절한다")
    @Test
    void rejectNonPositiveRankingPeriod() {
        StrategyDsl strategyDsl = strategyDsl(condition(14), 0);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> resolver.resolve(strategyDsl))
                .withMessage("Factor period must be positive");
    }

    private StrategyDsl strategyDsl(
            FilterNode filterNode,
            int rankingPeriod
    ) {
        return new StrategyDsl(
                Timeframe.ONE_HOUR,
                filterNode,
                new Ranking(FactorType.RETURN, rankingPeriod, RankingDirection.DESC),
                new Selection(5),
                new Rebalance(RebalancePeriod.WEEKLY)
        );
    }

    private ConditionFilterNode condition(int period) {
        return new ConditionFilterNode(
                new FactorOperand(FactorType.RSI, period),
                ComparisonOperator.GT,
                new ValueOperand(BigDecimal.valueOf(30))
        );
    }
}
