package com.ssafy.a107.asset.service;

import com.ssafy.a107.asset.domain.Asset;
import com.ssafy.a107.asset.domain.AssetTradingStatus;
import com.ssafy.a107.asset.domain.Exchange;
import com.ssafy.a107.asset.repository.AssetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class AssetReadService {
    private final AssetRepository assetRepository;

    public AssetReadService(AssetRepository assetRepository) {
        this.assetRepository = assetRepository;
    }

    public List<Asset> findChartAssets() {
        return assetRepository.findByExchangeAndQuoteCurrencyCodeAndTradingStatusOrderByExchangeMarketCodeAsc(
                Exchange.UPBIT, "KRW", AssetTradingStatus.TRADING);
    }
}
