package com.ssafy.a107.trading.simulation.domain;

import com.ssafy.a107.member.domain.LoginProvider;
import com.ssafy.a107.member.domain.Member;
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
import com.ssafy.a107.trading.backtest.domain.ExecutionPriceRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class SimulationRunTest {

    private static final Instant STARTED_AT = Instant.parse("2026-09-14T00:00:00Z");

    @DisplayName("모의투자 실행은 생성 상태와 초기 현금 잔액으로 시작한다")
    @Test
    void createSimulationRun() {
        SimulationRun simulationRun = simulationRun();

        assertThat(simulationRun.getStatus()).isEqualTo(SimulationRunStatus.CREATED);
        assertThat(simulationRun.getCashBalance()).isEqualByComparingTo("1000000");
        assertThat(simulationRun.getTimeframe()).isEqualTo(Timeframe.ONE_HOUR);
    }

    @DisplayName("생성 또는 중지된 모의투자 실행은 다시 시작할 수 있다")
    @Test
    void restartStoppedSimulationRun() {
        SimulationRun simulationRun = simulationRun();
        simulationRun.start(STARTED_AT);
        simulationRun.stop(STARTED_AT.plusSeconds(60));

        simulationRun.start(STARTED_AT.plusSeconds(120));

        assertThat(simulationRun.getStatus()).isEqualTo(SimulationRunStatus.RUNNING);
        assertThat(simulationRun.getStoppedAt()).isNull();
    }

    @DisplayName("동일하거나 과거 Candle은 중복 처리하지 않는다")
    @Test
    void ignoresDuplicateOrOutOfOrderCandle() {
        SimulationRun simulationRun = runningSimulationRun();
        Instant candleAt = STARTED_AT.plusSeconds(3600);

        assertThat(simulationRun.beginCandle(candleAt)).isTrue();
        assertThat(simulationRun.beginCandle(candleAt)).isFalse();
        assertThat(simulationRun.beginCandle(candleAt.minusSeconds(1))).isFalse();
    }

    @DisplayName("실행 시작 전 Candle은 처리하지 않는다")
    @Test
    void ignoresCandleBeforeSimulationStarts() {
        SimulationRun simulationRun = runningSimulationRun();

        assertThat(simulationRun.beginCandle(STARTED_AT.minusSeconds(1))).isFalse();
    }

    @DisplayName("실행 중이 아닌 모의투자는 Candle을 처리할 수 없다")
    @Test
    void cannotProcessCandleWhenNotRunning() {
        assertThatIllegalStateException()
                .isThrownBy(() -> simulationRun().beginCandle(STARTED_AT));
    }

    private SimulationRun runningSimulationRun() {
        SimulationRun simulationRun = simulationRun();
        simulationRun.start(STARTED_AT);
        return simulationRun;
    }

    private SimulationRun simulationRun() {
        Member member = new Member(
                LoginProvider.SSAFY,
                "simulation-member-107",
                "simulation@example.com",
                "모의투자 사용자"
        );
        Strategy strategy = new Strategy(member, "모의투자 전략", null, strategyDsl());
        return new SimulationRun(
                member,
                strategy,
                BigDecimal.valueOf(1_000_000),
                BigDecimal.valueOf(0.0005),
                BigDecimal.valueOf(5),
                ExecutionPriceRule.NEXT_CANDLE_OPEN,
                "engine-v1"
        );
    }

    private StrategyDsl strategyDsl() {
        return new StrategyDsl(
                Timeframe.ONE_HOUR,
                new ConditionFilterNode(
                        new FactorOperand(FactorType.RSI, 14),
                        ComparisonOperator.LT,
                        new ValueOperand(BigDecimal.valueOf(30))
                ),
                new Ranking(FactorType.RETURN, 20, RankingDirection.DESC),
                new Selection(5),
                new Rebalance(RebalancePeriod.WEEKLY)
        );
    }
}
