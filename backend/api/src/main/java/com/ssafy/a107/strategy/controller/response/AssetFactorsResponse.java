package com.ssafy.a107.strategy.controller.response;

import com.ssafy.a107.strategy.domain.dsl.Timeframe;
import com.ssafy.a107.strategy.service.AssetFactorSnapshot;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AssetFactorsResponse(UUID assetId, Timeframe timeframe, Instant asOf, List<FactorValueResponse> factors) {

    public static AssetFactorsResponse from(UUID assetId, Timeframe timeframe, AssetFactorSnapshot snapshot) {
        return new AssetFactorsResponse(assetId, timeframe, snapshot.asOf(),
                snapshot.values().stream().map(FactorValueResponse::from).toList());
    }
}
