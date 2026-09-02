package com.ssafy.a107.strategy.engine.portfolio;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Component
public class TargetPortfolioEvaluator {

    private static final int WEIGHT_SCALE = 10;

    public TargetPortfolio create(List<UUID> selectedAssetIds) {
        Objects.requireNonNull(selectedAssetIds);

        if (selectedAssetIds.isEmpty()) {
            return new TargetPortfolio(List.of(), BigDecimal.ONE);
        }

        BigDecimal targetWeight = BigDecimal.ONE.divide(
                BigDecimal.valueOf(selectedAssetIds.size()),
                WEIGHT_SCALE,
                RoundingMode.HALF_UP
        );
        List<TargetAssetWeight> assetWeights = new ArrayList<>(selectedAssetIds.size());
        for (UUID selectedAssetId : selectedAssetIds) {
            assetWeights.add(new TargetAssetWeight(selectedAssetId, targetWeight));
        }

        BigDecimal allocatedWeight = targetWeight.multiply(BigDecimal.valueOf(selectedAssetIds.size()));
        BigDecimal cashWeight = BigDecimal.ONE.subtract(allocatedWeight);
        return new TargetPortfolio(assetWeights, cashWeight);
    }
}
