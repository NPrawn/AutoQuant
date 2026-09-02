package com.ssafy.a107.strategy.engine.portfolio;

import java.math.BigDecimal;
import java.util.UUID;

public record TargetAssetWeight(
        UUID assetId,
        BigDecimal targetWeight
) {
}
