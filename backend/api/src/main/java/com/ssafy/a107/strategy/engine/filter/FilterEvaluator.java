package com.ssafy.a107.strategy.engine.filter;

import com.ssafy.a107.strategy.domain.dsl.ComparisonOperator;
import com.ssafy.a107.strategy.domain.dsl.ConditionFilterNode;
import com.ssafy.a107.strategy.domain.dsl.FactorOperand;
import com.ssafy.a107.strategy.domain.dsl.FilterNode;
import com.ssafy.a107.strategy.domain.dsl.GroupFilterNode;
import com.ssafy.a107.strategy.domain.dsl.Logic;
import com.ssafy.a107.strategy.domain.dsl.Operand;
import com.ssafy.a107.strategy.domain.dsl.ValueOperand;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Component
public class FilterEvaluator {

    public boolean evaluate(FilterNode filterNode, Map<FactorOperand, BigDecimal> factorValues) {
        Objects.requireNonNull(filterNode);
        Objects.requireNonNull(factorValues);

        return switch (filterNode) {
            case GroupFilterNode group -> evaluateGroup(group, factorValues);
            case ConditionFilterNode condition -> evaluateCondition(condition, factorValues);
        };
    }

    private boolean evaluateGroup(GroupFilterNode group, Map<FactorOperand, BigDecimal> factorValues) {
        return switch (group.logic()) {
            case AND -> group.children().stream().allMatch(child -> evaluate(child, factorValues));
            case OR -> group.children().stream().anyMatch(child -> evaluate(child, factorValues));
        };
    }

    private boolean evaluateCondition(ConditionFilterNode condition, Map<FactorOperand, BigDecimal> factorValues) {
        Optional<BigDecimal> left = resolveOperand(condition.left(), factorValues);
        Optional<BigDecimal> right = resolveOperand(condition.right(), factorValues);
        if (left.isEmpty() || right.isEmpty()) {
            return false;
        }

        int comparison = left.get().compareTo(right.get());
        return switch (condition.operator()) {
            case GT -> comparison > 0;
            case GTE -> comparison >= 0;
            case LT -> comparison < 0;
            case LTE -> comparison <= 0;
        };
    }

    private Optional<BigDecimal> resolveOperand(Operand operand, Map<FactorOperand, BigDecimal> factorValues) {
        return switch (operand) {
            case FactorOperand factorOperand -> Optional.ofNullable(factorValues.get(factorOperand));
            case ValueOperand valueOperand -> Optional.ofNullable(valueOperand.value());
        };
    }
}
