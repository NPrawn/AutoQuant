package com.ssafy.a107.strategy.engine;

import com.ssafy.a107.strategy.domain.dsl.FactorOperand;
import com.ssafy.a107.strategy.domain.dsl.StrategyDsl;
import com.ssafy.a107.strategy.engine.factor.Ta4jFactorCalculator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 한 번의 백테스트에서 재사용하는 전략 지표 시계열이다.
 */
public final class PreparedStrategyExecution {

    private final StrategyDsl strategyDsl;
    private final Map<UUID, Ta4jFactorCalculator.PreparedFactorSeries> factorsByAssetId;

    PreparedStrategyExecution(
            StrategyDsl strategyDsl,
            Map<UUID, Ta4jFactorCalculator.PreparedFactorSeries> factorsByAssetId
    ) {
        this.strategyDsl = Objects.requireNonNull(strategyDsl);
        this.factorsByAssetId = Map.copyOf(factorsByAssetId);
    }

    StrategyDsl strategyDsl() {
        return strategyDsl;
    }

    Map<UUID, Map<FactorOperand, BigDecimal>> calculateFactorValuesAt(
            Instant evaluationAt,
            List<UUID> assetIds
    ) {
        Objects.requireNonNull(evaluationAt);
        Objects.requireNonNull(assetIds);

        Map<UUID, Map<FactorOperand, BigDecimal>> valuesByAssetId = new LinkedHashMap<>();
        for (UUID assetId : assetIds) {
            Ta4jFactorCalculator.PreparedFactorSeries factorSeries = factorsByAssetId.get(assetId);
            if (factorSeries == null) {
                throw new IllegalArgumentException("Prepared strategy does not contain requested asset");
            }
            valuesByAssetId.put(assetId, factorSeries.calculateAt(evaluationAt));
        }
        return valuesByAssetId;
    }
}
