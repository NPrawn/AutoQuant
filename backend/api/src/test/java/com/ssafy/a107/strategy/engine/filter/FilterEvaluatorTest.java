package com.ssafy.a107.strategy.engine.filter;

import com.ssafy.a107.strategy.domain.dsl.ComparisonOperator;
import com.ssafy.a107.strategy.domain.dsl.ConditionFilterNode;
import com.ssafy.a107.strategy.domain.dsl.FactorOperand;
import com.ssafy.a107.strategy.domain.dsl.FactorType;
import com.ssafy.a107.strategy.domain.dsl.FilterNode;
import com.ssafy.a107.strategy.domain.dsl.GroupFilterNode;
import com.ssafy.a107.strategy.domain.dsl.Logic;
import com.ssafy.a107.strategy.domain.dsl.ValueOperand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class FilterEvaluatorTest {

    private final FilterEvaluator evaluator = new FilterEvaluator();

    @Test
    @DisplayName("중첩된 AND와 OR GROUP을 재귀적으로 평가한다")
    void evaluateNestedGroups() {
        // given
        FactorOperand rsi = new FactorOperand(FactorType.RSI, 14);
        FactorOperand volumeRatio = new FactorOperand(FactorType.VOLUME_RATIO, 20);
        FilterNode filters = new GroupFilterNode(
                Logic.OR,
                List.of(
                        new GroupFilterNode(
                                Logic.AND,
                                List.of(
                                        condition(rsi, ComparisonOperator.LT, new BigDecimal("30")),
                                        condition(volumeRatio, ComparisonOperator.GTE, new BigDecimal("1.5"))
                                )
                        ),
                        condition(new FactorOperand(FactorType.RETURN, 20), ComparisonOperator.GT, BigDecimal.TEN)
                )
        );
        Map<FactorOperand, BigDecimal> factorValues = Map.of(
                rsi, new BigDecimal("25"),
                volumeRatio, new BigDecimal("1.7")
        );

        // when
        boolean result = evaluator.evaluate(filters, factorValues);

        // then
        assertThat(result).isTrue();
    }

    @ParameterizedTest
    @MethodSource("comparisonCases")
    @DisplayName("비교 연산자를 적용해 CONDITION을 평가한다")
    void evaluateComparisonOperators(ComparisonOperator operator, BigDecimal factorValue, BigDecimal value, boolean expected) {
        // given
        FactorOperand rsi = new FactorOperand(FactorType.RSI, 14);
        ConditionFilterNode condition = condition(rsi, operator, value);

        // when
        boolean result = evaluator.evaluate(condition, Map.of(rsi, factorValue));

        // then
        assertThat(result).isEqualTo(expected);
    }

    @Test
    @DisplayName("FACTOR와 FACTOR Operand를 비교한다")
    void evaluateFactorToFactorCondition() {
        // given
        FactorOperand shortRsi = new FactorOperand(FactorType.RSI, 14);
        FactorOperand longRsi = new FactorOperand(FactorType.RSI, 60);
        ConditionFilterNode condition = new ConditionFilterNode(shortRsi, ComparisonOperator.LT, longRsi);

        // when
        boolean result = evaluator.evaluate(condition, Map.of(
                shortRsi, new BigDecimal("35"),
                longRsi, new BigDecimal("50")
        ));

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("VALUE와 FACTOR Operand를 비교한다")
    void evaluateValueToFactorCondition() {
        // given
        FactorOperand rsi = new FactorOperand(FactorType.RSI, 14);
        ConditionFilterNode condition = new ConditionFilterNode(
                new ValueOperand(new BigDecimal("30")),
                ComparisonOperator.LT,
                rsi
        );

        // when
        boolean result = evaluator.evaluate(condition, Map.of(rsi, new BigDecimal("35")));

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("VALUE와 VALUE Operand를 비교한다")
    void evaluateValueToValueCondition() {
        // given
        ConditionFilterNode condition = new ConditionFilterNode(
                new ValueOperand(new BigDecimal("30")),
                ComparisonOperator.LT,
                new ValueOperand(new BigDecimal("35"))
        );

        // when
        boolean result = evaluator.evaluate(condition, Map.of());

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("계산되지 않은 FACTOR Operand가 있으면 CONDITION을 통과시키지 않는다")
    void rejectConditionWithMissingFactorValue() {
        // given
        FactorOperand rsi = new FactorOperand(FactorType.RSI, 14);
        ConditionFilterNode condition = condition(rsi, ComparisonOperator.LT, new BigDecimal("30"));

        // when
        boolean result = evaluator.evaluate(condition, Map.of());

        // then
        assertThat(result).isFalse();
    }

    private static Stream<Arguments> comparisonCases() {
        return Stream.of(
                Arguments.of(ComparisonOperator.GT, new BigDecimal("11"), BigDecimal.TEN, true),
                Arguments.of(ComparisonOperator.GTE, BigDecimal.TEN, BigDecimal.TEN, true),
                Arguments.of(ComparisonOperator.LT, new BigDecimal("9"), BigDecimal.TEN, true),
                Arguments.of(ComparisonOperator.LTE, BigDecimal.TEN, BigDecimal.TEN, true)
        );
    }

    private ConditionFilterNode condition(FactorOperand left, ComparisonOperator operator, BigDecimal right) {
        return new ConditionFilterNode(left, operator, new ValueOperand(right));
    }
}
