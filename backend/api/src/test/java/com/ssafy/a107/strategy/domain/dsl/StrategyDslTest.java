package com.ssafy.a107.strategy.domain.dsl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StrategyDslTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final jakarta.validation.ValidatorFactory VALIDATOR_FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = VALIDATOR_FACTORY.getValidator();

    @AfterAll
    static void closeValidatorFactory() {
        VALIDATOR_FACTORY.close();
    }

    @DisplayName("중첩된 GROUP과 CONDITION DSL을 역직렬화한 뒤 같은 구조로 재직렬화한다")
    @Test
    void roundTripNestedFilterDsl() throws JsonProcessingException {
        String json = """
                {
                  "timeframe": "4H",
                  "filters": {
                    "type": "GROUP",
                    "logic": "OR",
                    "children": [
                      {
                        "type": "GROUP",
                        "logic": "AND",
                        "children": [
                          {
                            "type": "CONDITION",
                            "left": { "type": "FACTOR", "factor": "RSI", "period": 20 },
                            "operator": "LT",
                            "right": { "type": "VALUE", "value": 30 }
                          }
                        ]
                      },
                      {
                        "type": "CONDITION",
                        "left": { "type": "FACTOR", "factor": "RETURN", "period": 20 },
                        "operator": "GT",
                        "right": { "type": "FACTOR", "factor": "RSI", "period": 60 }
                      }
                    ]
                  },
                  "ranking": { "factor": "RETURN", "period": 20, "direction": "DESC" },
                  "selection": { "topN": 5 },
                  "rebalance": { "period": "WEEKLY" }
                }
                """;

        StrategyDsl strategyDsl = OBJECT_MAPPER.readValue(json, StrategyDsl.class);
        JsonNode serialized = OBJECT_MAPPER.readTree(OBJECT_MAPPER.writeValueAsString(strategyDsl));

        assertThat(strategyDsl.timeframe()).isEqualTo(Timeframe.FOUR_HOURS);
        assertThat(strategyDsl.filters()).isInstanceOf(GroupFilterNode.class);
        assertThat(serialized.at("/filters/type").asText()).isEqualTo("GROUP");
        assertThat(serialized.at("/filters/children/0/type").asText()).isEqualTo("GROUP");
        assertThat(serialized.at("/filters/children/0/children/0/type").asText()).isEqualTo("CONDITION");
        assertThat(serialized.at("/filters/children/1/right/type").asText()).isEqualTo("FACTOR");
    }

    @DisplayName("topN이 0 이하면 DSL 검증에 실패한다")
    @Test
    void rejectNonPositiveTopN() {
        StrategyDsl strategyDsl = new StrategyDsl(
                Timeframe.ONE_HOUR,
                validCondition(),
                validRanking(),
                new Selection(0),
                new Rebalance(RebalancePeriod.WEEKLY)
        );

        Set<ConstraintViolation<StrategyDsl>> violations = VALIDATOR.validate(strategyDsl);

        assertThat(violations).extracting(ConstraintViolation::getPropertyPath)
                .extracting(Object::toString)
                .contains("selection.topN");
    }

    @DisplayName("Factor와 Ranking의 period가 0 이하면 DSL 검증에 실패한다")
    @Test
    void rejectNonPositiveFactorAndRankingPeriods() {
        StrategyDsl strategyDsl = new StrategyDsl(
                Timeframe.ONE_HOUR,
                new ConditionFilterNode(new FactorOperand(FactorType.RSI, 0), ComparisonOperator.LT, new ValueOperand(BigDecimal.TEN)),
                new Ranking(FactorType.RETURN, -1, RankingDirection.DESC),
                new Selection(5),
                new Rebalance(RebalancePeriod.WEEKLY)
        );

        Set<ConstraintViolation<StrategyDsl>> violations = VALIDATOR.validate(strategyDsl);

        assertThat(violations).extracting(ConstraintViolation::getPropertyPath)
                .extracting(Object::toString)
                .contains("filters.left.period", "ranking.period");
    }

    @DisplayName("GROUP에 logic 또는 child가 없으면 DSL 검증에 실패한다")
    @Test
    void rejectGroupWithoutLogicOrChildren() {
        StrategyDsl strategyDsl = new StrategyDsl(
                Timeframe.ONE_HOUR,
                new GroupFilterNode(null, List.of()),
                validRanking(),
                new Selection(5),
                new Rebalance(RebalancePeriod.WEEKLY)
        );

        Set<ConstraintViolation<StrategyDsl>> violations = VALIDATOR.validate(strategyDsl);

        assertThat(violations).extracting(ConstraintViolation::getPropertyPath)
                .extracting(Object::toString)
                .contains("filters.logic", "filters.children");
    }

    @DisplayName("CONDITION의 left, operator, right가 없으면 DSL 검증에 실패한다")
    @Test
    void rejectConditionWithoutRequiredFields() {
        StrategyDsl strategyDsl = validDsl(new ConditionFilterNode(null, null, null));

        Set<ConstraintViolation<StrategyDsl>> violations = VALIDATOR.validate(strategyDsl);

        assertThat(violations).extracting(ConstraintViolation::getPropertyPath)
                .extracting(Object::toString)
                .contains("filters.left", "filters.operator", "filters.right");
    }

    @DisplayName("FACTOR operand의 factor 또는 period가 없으면 DSL 검증에 실패한다")
    @Test
    void rejectFactorOperandWithoutFactorOrPeriod() {
        StrategyDsl strategyDsl = validDsl(
                new ConditionFilterNode(new FactorOperand(null, null), ComparisonOperator.GT, new ValueOperand(BigDecimal.ONE))
        );

        Set<ConstraintViolation<StrategyDsl>> violations = VALIDATOR.validate(strategyDsl);

        assertThat(violations).extracting(ConstraintViolation::getPropertyPath)
                .extracting(Object::toString)
                .contains("filters.left.factor", "filters.left.period");
    }

    @DisplayName("VALUE operand의 value가 없으면 DSL 검증에 실패한다")
    @Test
    void rejectValueOperandWithoutValue() {
        StrategyDsl strategyDsl = validDsl(
                new ConditionFilterNode(new FactorOperand(FactorType.RSI, 14), ComparisonOperator.LT, new ValueOperand(null))
        );

        Set<ConstraintViolation<StrategyDsl>> violations = VALIDATOR.validate(strategyDsl);

        assertThat(violations).extracting(ConstraintViolation::getPropertyPath)
                .extracting(Object::toString)
                .contains("filters.right.value");
    }

    @DisplayName("지원하지 않는 enum 값은 DSL 역직렬화에서 거부한다")
    @Test
    void rejectUnsupportedEnumValues() {
        String json = """
                {
                  "timeframe": "2H",
                  "filters": {
                    "type": "CONDITION",
                    "left": { "type": "FACTOR", "factor": "UNKNOWN", "period": 14 },
                    "operator": "LT",
                    "right": { "type": "VALUE", "value": 30 }
                  },
                  "ranking": { "factor": "RETURN", "period": 20, "direction": "DESC" },
                  "selection": { "topN": 5 },
                  "rebalance": { "period": "WEEKLY" }
                }
                """;

        assertThatThrownBy(() -> OBJECT_MAPPER.readValue(json, StrategyDsl.class))
                .isInstanceOf(JsonProcessingException.class);
    }

    private StrategyDsl validDsl(FilterNode filters) {
        return new StrategyDsl(
                Timeframe.ONE_HOUR,
                filters,
                validRanking(),
                new Selection(5),
                new Rebalance(RebalancePeriod.WEEKLY)
        );
    }

    private Ranking validRanking() {
        return new Ranking(FactorType.RETURN, 20, RankingDirection.DESC);
    }

    private ConditionFilterNode validCondition() {
        return new ConditionFilterNode(
                new FactorOperand(FactorType.RSI, 14),
                ComparisonOperator.LT,
                new ValueOperand(BigDecimal.TEN)
        );
    }
}
