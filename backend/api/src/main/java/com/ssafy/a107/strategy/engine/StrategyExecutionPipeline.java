package com.ssafy.a107.strategy.engine;

import com.ssafy.a107.candle.domain.MinuteCandle;
import com.ssafy.a107.strategy.domain.dsl.ConditionFilterNode;
import com.ssafy.a107.strategy.domain.dsl.FactorOperand;
import com.ssafy.a107.strategy.domain.dsl.FilterNode;
import com.ssafy.a107.strategy.domain.dsl.GroupFilterNode;
import com.ssafy.a107.strategy.domain.dsl.Operand;
import com.ssafy.a107.strategy.domain.dsl.Ranking;
import com.ssafy.a107.strategy.domain.dsl.StrategyDsl;
import com.ssafy.a107.strategy.domain.dsl.ValueOperand;
import com.ssafy.a107.strategy.engine.factor.Ta4jFactorCalculator;
import com.ssafy.a107.strategy.engine.filter.FilterEvaluator;
import com.ssafy.a107.strategy.engine.portfolio.TargetPortfolio;
import com.ssafy.a107.strategy.engine.portfolio.TargetPortfolioEvaluator;
import com.ssafy.a107.strategy.engine.ranking.RankingEvaluator;
import com.ssafy.a107.strategy.engine.selection.SelectionEvaluator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Component
public class StrategyExecutionPipeline {

    private static final Logger log = LoggerFactory.getLogger(StrategyExecutionPipeline.class);

    private final Ta4jFactorCalculator factorCalculator;
    private final FilterEvaluator filterEvaluator;
    private final RankingEvaluator rankingEvaluator;
    private final SelectionEvaluator selectionEvaluator;
    private final TargetPortfolioEvaluator targetPortfolioEvaluator;

    public StrategyExecutionPipeline(
            Ta4jFactorCalculator factorCalculator,
            FilterEvaluator filterEvaluator,
            RankingEvaluator rankingEvaluator,
            SelectionEvaluator selectionEvaluator,
            TargetPortfolioEvaluator targetPortfolioEvaluator
    ) {
        this.factorCalculator = factorCalculator;
        this.filterEvaluator = filterEvaluator;
        this.rankingEvaluator = rankingEvaluator;
        this.selectionEvaluator = selectionEvaluator;
        this.targetPortfolioEvaluator = targetPortfolioEvaluator;
    }

    public TargetPortfolio execute(
            StrategyExecutionContext strategyExecutionContext
    ) {
        Objects.requireNonNull(strategyExecutionContext);

        return executeInternal(
                strategyExecutionContext.strategyDsl(),
                strategyExecutionContext.candlesByAssetId(),
                false
        );
    }

    public TargetPortfolio executeExcludingInsufficientCandleAssets(
            StrategyExecutionContext strategyExecutionContext
    ) {
        Objects.requireNonNull(strategyExecutionContext);

        return executeInternal(
                strategyExecutionContext.strategyDsl(),
                strategyExecutionContext.candlesByAssetId(),
                true
        );
    }

    private TargetPortfolio executeInternal(
            StrategyDsl strategyDsl,
            Map<UUID, List<MinuteCandle>> assetCandles,
            boolean excludeInsufficientCandleAssets
    ) {
        Objects.requireNonNull(strategyDsl);
        Objects.requireNonNull(assetCandles);

        validateSupportedStrategyDsl(strategyDsl);
        validateCandleTimeframes(strategyDsl, assetCandles);

        Set<FactorOperand> requiredFactors = collectRequiredFactors(strategyDsl);
        Map<UUID, Map<FactorOperand, BigDecimal>> assetFactorValues = calculateFactorValues(assetCandles, requiredFactors);
        Map<UUID, Map<FactorOperand, BigDecimal>> completeAssetFactorValues = excludeInsufficientCandleAssets
                ? excludeAssetsWithInsufficientCandles(assetFactorValues, requiredFactors)
                : validateRequiredFactorValues(assetFactorValues, requiredFactors);
        Map<UUID, Map<FactorOperand, BigDecimal>> eligibleAssetFactorValues = filterEligibleAssets(
                completeAssetFactorValues,
                strategyDsl
        );
        List<UUID> rankedAssetIds = rankingEvaluator.rank(eligibleAssetFactorValues, strategyDsl.ranking());
        List<UUID> selectedAssetIds = selectionEvaluator.select(rankedAssetIds, strategyDsl.selection());
        return targetPortfolioEvaluator.create(selectedAssetIds);
    }

    private void validateSupportedStrategyDsl(StrategyDsl strategyDsl) {
        if (strategyDsl.filters() == null) {
            throw unsupportedStrategyDsl("filters are required");
        }
        if (strategyDsl.ranking() == null) {
            throw unsupportedStrategyDsl("ranking is required");
        }
        if (strategyDsl.selection() == null) {
            throw unsupportedStrategyDsl("selection is required");
        }
        if (strategyDsl.rebalance() == null) {
            throw unsupportedStrategyDsl("rebalance is required");
        }
        if (strategyDsl.timeframe() == null) {
            throw unsupportedStrategyDsl("timeframe is required");
        }

        validateFilterNode(strategyDsl.filters());
        validateRanking(strategyDsl.ranking());

        if (strategyDsl.selection().topN() == null || strategyDsl.selection().topN() <= 0) {
            throw unsupportedStrategyDsl("selection topN must be positive");
        }
        if (strategyDsl.rebalance().period() == null) {
            throw unsupportedStrategyDsl("rebalance period is required");
        }
    }

    private void validateFilterNode(FilterNode filterNode) {
        if (filterNode == null) {
            throw unsupportedStrategyDsl("filter node is required");
        }

        if (filterNode instanceof GroupFilterNode group) {
            if (group.logic() == null || group.children() == null || group.children().isEmpty()) {
                throw unsupportedStrategyDsl("filter group is invalid");
            }
            group.children().forEach(this::validateFilterNode);
            return;
        }

        ConditionFilterNode condition = (ConditionFilterNode) filterNode;
        if (condition.operator() == null) {
            throw unsupportedStrategyDsl("filter operator is required");
        }
        validateOperand(condition.left());
        validateOperand(condition.right());
    }

    private void validateRanking(Ranking ranking) {
        if (ranking.direction() == null) {
            throw unsupportedStrategyDsl("ranking direction is required");
        }
        validateFactorOperand(new FactorOperand(ranking.factor(), ranking.period()));
    }

    private void validateOperand(Operand operand) {
        if (operand == null) {
            throw unsupportedStrategyDsl("filter operand is required");
        }

        if (operand instanceof FactorOperand factorOperand) {
            validateFactorOperand(factorOperand);
            return;
        }

        ValueOperand valueOperand = (ValueOperand) operand;
        if (valueOperand.value() == null) {
            throw unsupportedStrategyDsl("value operand is required");
        }
    }

    private void validateFactorOperand(FactorOperand factorOperand) {
        if (factorOperand.factor() == null || factorOperand.period() == null || factorOperand.period() <= 0) {
            throw unsupportedStrategyDsl("factor operand is invalid");
        }
    }

    private StrategyExecutionException unsupportedStrategyDsl(String reason) {
        return new StrategyExecutionException("Unsupported strategy DSL: " + reason);
    }

    private Set<FactorOperand> collectRequiredFactors(StrategyDsl strategyDsl) {
        Set<FactorOperand> requiredFactors = new LinkedHashSet<>();
        collectFilterFactors(strategyDsl.filters(), requiredFactors);
        requiredFactors.add(new FactorOperand(strategyDsl.ranking().factor(), strategyDsl.ranking().period()));
        return requiredFactors;
    }

    private void collectFilterFactors(FilterNode filterNode, Set<FactorOperand> requiredFactors) {
        if (filterNode instanceof GroupFilterNode group) {
            for (FilterNode child : group.children()) {
                collectFilterFactors(child, requiredFactors);
            }
            return;
        }

        ConditionFilterNode condition = (ConditionFilterNode) filterNode;
        collectOperandFactor(condition.left(), requiredFactors);
        collectOperandFactor(condition.right(), requiredFactors);
    }

    private void collectOperandFactor(Operand operand, Set<FactorOperand> requiredFactors) {
        if (operand instanceof FactorOperand factorOperand) {
            requiredFactors.add(factorOperand);
        }
    }

    private Map<UUID, Map<FactorOperand, BigDecimal>> calculateFactorValues(
            Map<UUID, List<MinuteCandle>> assetCandles,
            Set<FactorOperand> requiredFactors
    ) {
        Map<UUID, Map<FactorOperand, BigDecimal>> assetFactorValues = new LinkedHashMap<>();
        for (Map.Entry<UUID, List<MinuteCandle>> assetCandleEntry : assetCandles.entrySet()) {
            Map<FactorOperand, BigDecimal> factorValues = new LinkedHashMap<>();
            for (FactorOperand requiredFactor : requiredFactors) {
                Optional<BigDecimal> factorValue = factorCalculator.calculate(
                        requiredFactor.factor(),
                        requiredFactor.period(),
                        assetCandleEntry.getValue()
                );
                if (factorValue.isPresent()) {
                    factorValues.put(requiredFactor, factorValue.get());
                }
            }
            assetFactorValues.put(assetCandleEntry.getKey(), Map.copyOf(factorValues));
        }
        return assetFactorValues;
    }

    private Map<UUID, Map<FactorOperand, BigDecimal>> validateRequiredFactorValues(
            Map<UUID, Map<FactorOperand, BigDecimal>> assetFactorValues,
            Set<FactorOperand> requiredFactors
    ) {
        for (Map.Entry<UUID, Map<FactorOperand, BigDecimal>> assetFactorValueEntry : assetFactorValues.entrySet()) {
            List<FactorOperand> missingFactors = requiredFactors.stream()
                    .filter(requiredFactor -> !assetFactorValueEntry.getValue().containsKey(requiredFactor))
                    .toList();

            if (!missingFactors.isEmpty()) {
                throw new InsufficientCandleDataException(
                        "Insufficient candle data for asset "
                                + assetFactorValueEntry.getKey()
                                + ": "
                                + missingFactors
                );
            }
        }
        return assetFactorValues;
    }

    private Map<UUID, Map<FactorOperand, BigDecimal>> excludeAssetsWithInsufficientCandles(
            Map<UUID, Map<FactorOperand, BigDecimal>> assetFactorValues,
            Set<FactorOperand> requiredFactors
    ) {
        if (assetFactorValues.isEmpty()) {
            return Map.of();
        }

        Map<UUID, Map<FactorOperand, BigDecimal>> completeAssetFactorValues = new LinkedHashMap<>();
        List<UUID> excludedAssetIds = new ArrayList<>();
        for (Map.Entry<UUID, Map<FactorOperand, BigDecimal>> assetFactorValueEntry : assetFactorValues.entrySet()) {
            if (assetFactorValueEntry.getValue().keySet().containsAll(requiredFactors)) {
                completeAssetFactorValues.put(
                        assetFactorValueEntry.getKey(),
                        assetFactorValueEntry.getValue()
                );
            } else {
                excludedAssetIds.add(assetFactorValueEntry.getKey());
            }
        }

        if (completeAssetFactorValues.isEmpty()) {
            throw new InsufficientCandleDataException(
                    "Insufficient candle data for all candidate assets"
            );
        }

        if (!excludedAssetIds.isEmpty()) {
            log.info(
                    "Excluded backtest candidate assets with insufficient candle data: {}",
                    excludedAssetIds
            );
        }
        return completeAssetFactorValues;
    }

    private Map<UUID, Map<FactorOperand, BigDecimal>> filterEligibleAssets(
            Map<UUID, Map<FactorOperand, BigDecimal>> assetFactorValues,
            StrategyDsl strategyDsl
    ) {
        Map<UUID, Map<FactorOperand, BigDecimal>> eligibleAssetFactorValues = new LinkedHashMap<>();
        for (Map.Entry<UUID, Map<FactorOperand, BigDecimal>> assetFactorValueEntry : assetFactorValues.entrySet()) {
            boolean isEligible = filterEvaluator.evaluate(strategyDsl.filters(), assetFactorValueEntry.getValue());
            if (isEligible) {
                eligibleAssetFactorValues.put(assetFactorValueEntry.getKey(), assetFactorValueEntry.getValue());
            }
        }
        return eligibleAssetFactorValues;
    }

    private void validateCandleTimeframes(StrategyDsl strategyDsl, Map<UUID, List<MinuteCandle>> assetCandles) {
        int expectedIntervalMinutes = strategyDsl.timeframe().getIntervalMinutes();
        for (List<MinuteCandle> candles : assetCandles.values()) {
            for (MinuteCandle candle : candles) {
                if (candle.getId().getIntervalMinutes() != expectedIntervalMinutes) {
                    throw new IllegalArgumentException("candle interval must match strategy timeframe");
                }
            }
        }
    }
}
