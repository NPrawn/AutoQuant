package com.ssafy.a107.asset.service;

import com.ssafy.a107.asset.domain.Asset;
import com.ssafy.a107.asset.domain.Exchange;
import com.ssafy.a107.asset.domain.MarketDataCollectionTarget;
import com.ssafy.a107.asset.repository.AssetRepository;
import com.ssafy.a107.asset.repository.MarketDataCollectionTargetRepository;
import com.ssafy.a107.asset.service.port.UpbitMarketInformation;
import com.ssafy.a107.common.exception.ApiErrorCode;
import com.ssafy.a107.common.exception.ApiException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AssetCommandService {

    private final AssetRepository assetRepository;
    private final MarketDataCollectionTargetRepository collectionTargetRepository;

    public AssetCommandService(
            AssetRepository assetRepository,
            MarketDataCollectionTargetRepository collectionTargetRepository
    ) {
        this.assetRepository = assetRepository;
        this.collectionTargetRepository = collectionTargetRepository;
    }

    public AssetRegistrationResult createAsset(
            Exchange exchange,
            UpbitMarketInformation marketInformation,
            String baseCurrencyCode,
            String quoteCurrencyCode,
            Instant tradingSupportStartedAt
    ) {
        if (assetRepository.existsByExchangeAndExchangeMarketCode(
                exchange,
                marketInformation.exchangeMarketCode()
        )) {
            throw new ApiException(ApiErrorCode.ASSET_ALREADY_EXISTS);
        }

        Asset asset = new Asset(
                exchange,
                marketInformation.exchangeMarketCode(),
                baseCurrencyCode,
                quoteCurrencyCode,
                marketInformation.koreanName(),
                marketInformation.englishName(),
                tradingSupportStartedAt
        );
        Asset savedAsset = saveAsset(asset);

        MarketDataCollectionTarget collectionTarget = new MarketDataCollectionTarget(savedAsset);
        MarketDataCollectionTarget savedCollectionTarget = collectionTargetRepository.save(collectionTarget);

        return AssetRegistrationResult.from(savedAsset, savedCollectionTarget);
    }

    private Asset saveAsset(Asset asset) {
        try {
            return assetRepository.saveAndFlush(asset);
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(ApiErrorCode.ASSET_ALREADY_EXISTS, exception);
        }
    }
}
