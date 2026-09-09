package com.ssafy.a107.trading.backtest.engine.marketdata;

import com.ssafy.a107.strategy.domain.dsl.*;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class BacktestWarmupCandleCountResolver {

    public int resolve(StrategyDsl strategyDsl) {
        Objects.requireNonNull(strategyDsl);

        int maximumPeriod = requirePositive(
                strategyDsl.ranking().period()
        );
        maximumPeriod = Math.max(
                maximumPeriod,
                resolveFilterMaximumPeriod(strategyDsl.filters())
        );

        return maximumPeriod + 1;
    }

    private int resolveFilterMaximumPeriod(FilterNode filterNode) {
        if (filterNode instanceof GroupFilterNode groupFilterNode) {
            return groupFilterNode.children().stream()
                    .mapToInt(this::resolveFilterMaximumPeriod)
                    .max()
                    .orElse(0);
        }

        ConditionFilterNode conditionFilterNode = (ConditionFilterNode) filterNode;
        return Math.max(
                resolveOperandPeriod(conditionFilterNode.left()),
                resolveOperandPeriod(conditionFilterNode.right())
        );
    }

    private int resolveOperandPeriod(Operand operand) {
        if (operand instanceof FactorOperand factorOperand) {
            return requirePositive(factorOperand.period());
        }

        return 0;
    }

    private int requirePositive(Integer period) {
        if (period == null || period <= 0) {
            throw new IllegalArgumentException(
                    "Factor period must be positive"
            );
        }

        return period;
    }
}
