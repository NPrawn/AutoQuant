package com.ssafy.a107.trading.backtest.engine;

import com.ssafy.a107.candle.domain.MinuteCandle;
import com.ssafy.a107.candle.domain.MinuteCandleFixture;
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
import com.ssafy.a107.strategy.engine.StrategyExecutionContext;
import com.ssafy.a107.trading.backtest.domain.BacktestRun;
import com.ssafy.a107.trading.backtest.engine.marketdata.BacktestCandleWindow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BacktestStrategyExecutionContextFactoryTest {

    private static final UUID ASSET_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final Instant EVALUATION_START = Instant.parse("2026-01-02T00:00:00Z");
    private static final Instant EVALUATION_END = Instant.parse("2026-01-02T04:00:00Z");

    private final BacktestStrategyExecutionContextFactory factory =
            new BacktestStrategyExecutionContextFactory();

    @DisplayName("Warm-up과 현재 봉까지의 캔들로 전략 실행 문맥을 생성한다")
    @Test
    void createContextWithoutFutureCandles() {
        BacktestRun backtestRun = backtestRun(Timeframe.ONE_HOUR);
        BacktestCandleWindow candleWindow = candleWindow(Timeframe.ONE_HOUR);

        StrategyExecutionContext context = factory.create(
                backtestRun,
                candleWindow,
                Instant.parse("2026-01-02T01:00:00Z")
        );

        assertThat(context.evaluationAt())
                .isEqualTo(Instant.parse("2026-01-02T02:00:00Z"));
        assertThat(context.candlesByAssetId().get(ASSET_ID))
                .extracting(candle -> candle.getId().getOpenTime())
                .containsExactly(
                        Instant.parse("2026-01-01T23:00:00Z"),
                        Instant.parse("2026-01-02T00:00:00Z"),
                        Instant.parse("2026-01-02T01:00:00Z")
                );
    }

    @DisplayName("평가 구간 밖의 봉으로는 전략 실행 문맥을 생성할 수 없다")
    @Test
    void rejectCandleOutsideEvaluationWindow() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> factory.create(
                        backtestRun(Timeframe.ONE_HOUR),
                        candleWindow(Timeframe.ONE_HOUR),
                        EVALUATION_END
                ))
                .withMessage("Candle open time must be within evaluation window");
    }

    @DisplayName("백테스트와 캔들 구간의 시간 프레임이 다르면 전략 실행 문맥을 생성할 수 없다")
    @Test
    void rejectCandleWindowWithDifferentTimeframe() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> factory.create(
                        backtestRun(Timeframe.ONE_HOUR),
                        candleWindow(Timeframe.FOUR_HOURS),
                        EVALUATION_START
                ))
                .withMessage("Backtest timeframe must match candle window");
    }

    private BacktestRun backtestRun(Timeframe timeframe) {
        BacktestRun backtestRun = mock(BacktestRun.class);
        Strategy strategy = mock(Strategy.class);

        when(backtestRun.getTimeframe()).thenReturn(timeframe);
        when(backtestRun.getStrategy()).thenReturn(strategy);
        when(strategy.getStrategyDsl()).thenReturn(strategyDsl(timeframe));

        return backtestRun;
    }

    private BacktestCandleWindow candleWindow(Timeframe timeframe) {
        return new BacktestCandleWindow(
                timeframe,
                EVALUATION_START,
                EVALUATION_END,
                Map.of(ASSET_ID, List.of(
                        candle(timeframe, Instant.parse("2026-01-01T23:00:00Z")),
                        candle(timeframe, EVALUATION_START),
                        candle(timeframe, Instant.parse("2026-01-02T01:00:00Z")),
                        candle(timeframe, Instant.parse("2026-01-02T02:00:00Z")),
                        candle(timeframe, Instant.parse("2026-01-02T03:00:00Z"))
                ))
        );
    }

    private StrategyDsl strategyDsl(Timeframe timeframe) {
        return new StrategyDsl(
                timeframe,
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

    private MinuteCandle candle(Timeframe timeframe, Instant openTime) {
        return MinuteCandleFixture.minuteCandle(
                ASSET_ID,
                timeframe.getIntervalMinutes(),
                openTime,
                BigDecimal.ONE,
                BigDecimal.ONE
        );
    }
}
