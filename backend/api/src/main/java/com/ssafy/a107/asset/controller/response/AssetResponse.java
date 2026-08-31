package com.ssafy.a107.asset.controller.response;

import com.ssafy.a107.asset.domain.AssetTradingStatus;
import com.ssafy.a107.asset.domain.Exchange;
import com.ssafy.a107.asset.domain.MarketDataCollectionStatus;
import com.ssafy.a107.asset.service.AssetRegistrationResult;

import java.time.Instant;
import java.util.UUID;

public record AssetResponse(
        UUID assetId,
        Exchange exchange,
        String exchangeMarketCode,
        String baseCurrencyCode,
        String quoteCurrencyCode,
        String koreanName,
        String englishName,
        AssetTradingStatus tradingStatus,
        MarketDataCollectionStatus collectionStatus,
        Instant createdAt
) {

    public static AssetResponse from(AssetRegistrationResult result) {
        return new AssetResponse(
                result.assetId(),
                result.exchange(),
                result.exchangeMarketCode(),
                result.baseCurrencyCode(),
                result.quoteCurrencyCode(),
                result.koreanName(),
                result.englishName(),
                result.tradingStatus(),
                result.collectionStatus(),
                result.createdAt()
        );
    }
}
