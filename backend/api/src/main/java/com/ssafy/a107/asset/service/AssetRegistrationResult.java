package com.ssafy.a107.asset.service;

import com.ssafy.a107.asset.domain.Asset;
import com.ssafy.a107.asset.domain.AssetTradingStatus;
import com.ssafy.a107.asset.domain.Exchange;
import com.ssafy.a107.asset.domain.MarketDataCollectionStatus;
import com.ssafy.a107.asset.domain.MarketDataCollectionTarget;

import java.time.Instant;
import java.util.UUID;

public record AssetRegistrationResult(
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

    public static AssetRegistrationResult from(
            Asset asset,
            MarketDataCollectionTarget collectionTarget
    ) {
        return new AssetRegistrationResult(
                asset.getAssetId(),
                asset.getExchange(),
                asset.getExchangeMarketCode(),
                asset.getBaseCurrencyCode(),
                asset.getQuoteCurrencyCode(),
                asset.getKoreanName(),
                asset.getEnglishName(),
                asset.getTradingStatus(),
                collectionTarget.getCollectionStatus(),
                asset.getCreatedAt()
        );
    }
}
