package com.ssafy.a107.asset.repository;

import com.ssafy.a107.asset.domain.Asset;
import com.ssafy.a107.asset.domain.Exchange;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AssetRepository extends JpaRepository<Asset, UUID> {

    boolean existsByExchangeAndExchangeMarketCode(Exchange exchange, String exchangeMarketCode);
}
