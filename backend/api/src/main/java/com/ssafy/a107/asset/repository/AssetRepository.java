package com.ssafy.a107.asset.repository;

import com.ssafy.a107.asset.domain.Asset;
import com.ssafy.a107.asset.domain.Exchange;
import com.ssafy.a107.asset.domain.AssetTradingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.List;

public interface AssetRepository extends JpaRepository<Asset, UUID> {

    boolean existsByExchangeAndExchangeMarketCode(Exchange exchange, String exchangeMarketCode);

    List<Asset> findByExchangeAndQuoteCurrencyCodeAndTradingStatusOrderByExchangeMarketCodeAsc(
            Exchange exchange, String quoteCurrencyCode, AssetTradingStatus tradingStatus);
}
