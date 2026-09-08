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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StrategyExecutionContextTest {

    private static final UUID ASSET_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final Instant EVALUATION_AT = Instant.parse("2026-01-02T01:00:00Z");

    @DisplayName("완료된 캔들을 방어 복사해 전략 실행 문맥으로 생성한다")
    @Test
    void createContextWithCompletedCandles() {
        List<MinuteCandle> candles = new ArrayList<>(List.of(
                candle(Timeframe.ONE_HOUR, Instant.parse("2026-01-02T00:00:00Z"))
        ));
        Map<UUID, List<MinuteCandle>> candlesByAssetId = new LinkedHashMap<>();
        candlesByAssetId.put(ASSET_ID, candles);

        StrategyExecutionContext context = new StrategyExecutionContext(
                strategyDsl(),
                EVALUATION_AT,
                candlesByAssetId
        );
        candles.clear();
        candlesByAssetId.clear();

        assertThat(context.candlesByAssetId().get(ASSET_ID)).hasSize(1);
        assertThatThrownBy(() -> context.candlesByAssetId().put(ASSET_ID, List.of()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @DisplayName("평가 시각에 아직 종료되지 않은 캔들은 전략 실행 문맥에 포함할 수 없다")
    @Test
    void rejectCandleNotClosedAtEvaluationTime() {
        MinuteCandle incompleteCandle = candle(
                Timeframe.ONE_HOUR,
                Instant.parse("2026-01-02T01:00:00Z")
        );

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new StrategyExecutionContext(
                        strategyDsl(),
                        EVALUATION_AT,
                        Map.of(ASSET_ID, List.of(incompleteCandle))
                ))
                .withMessage("Candle must be closed at evaluation time");
    }

    @DisplayName("전략 시간 프레임과 다른 캔들은 전략 실행 문맥에 포함할 수 없다")
    @Test
    void rejectCandleWithDifferentTimeframe() {
        MinuteCandle fourHourCandle = candle(
                Timeframe.FOUR_HOURS,
                Instant.parse("2026-01-01T20:00:00Z")
        );

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new StrategyExecutionContext(
                        strategyDsl(),
                        EVALUATION_AT,
                        Map.of(ASSET_ID, List.of(fourHourCandle))
                ))
                .withMessage("Candle interval must match strategy timeframe");
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
