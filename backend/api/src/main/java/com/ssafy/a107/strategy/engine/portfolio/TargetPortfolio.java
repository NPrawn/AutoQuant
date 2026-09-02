package com.ssafy.a107.strategy.engine.portfolio;

import java.math.BigDecimal;
import java.util.List;

public record TargetPortfolio(
        List<TargetAssetWeight> assetWeights,
        BigDecimal cashWeight
) {

    public TargetPortfolio {
        assetWeights = List.copyOf(assetWeights);
    }
}
