package com.ssafy.a107.asset.controller.response;

import com.ssafy.a107.asset.domain.Asset;

import java.util.UUID;

public record ChartAssetResponse(UUID assetId, String marketCode, String koreanName) {
    public static ChartAssetResponse from(Asset asset) {
        return new ChartAssetResponse(asset.getAssetId(), asset.getExchangeMarketCode(), asset.getKoreanName());
    }
}
