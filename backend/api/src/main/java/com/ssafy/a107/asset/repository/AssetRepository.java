package com.ssafy.a107.asset.repository;

import com.ssafy.a107.asset.domain.Asset;
import com.ssafy.a107.asset.domain.Exchange;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AssetRepository extends JpaRepository<Asset, UUID> {

    boolean existsByExchangeAndExchangeMarketCode(Exchange exchange, String exchangeMarketCode);

    @Query("""
        select asset
        from Asset asset
        where asset.exchange = :exchange
          and asset.quoteCurrencyCode = :quoteCurrencyCode
          and (
              asset.tradingSupportStartedAt is null
              or asset.tradingSupportStartedAt <= :evaluationAt
          )
          and (
              asset.tradingSupportEndedAt is null
              or asset.tradingSupportEndedAt > :evaluationAt
          )
        order by asset.assetId asc
        """)
    List<Asset> findAllAvailableAt(
            @Param("exchange") Exchange exchange,
            @Param("quoteCurrencyCode") String quoteCurrencyCode,
            @Param("evaluationAt") Instant evaluationAt
    );

    @Query("""
        select asset
        from Asset asset
        where asset.exchange = :exchange
          and asset.quoteCurrencyCode = :quoteCurrencyCode
          and (
              asset.tradingSupportStartedAt is null
              or asset.tradingSupportStartedAt < :periodEnd
          )
          and (
              asset.tradingSupportEndedAt is null
              or asset.tradingSupportEndedAt > :periodStart
          )
        order by asset.assetId asc
        """)
    List<Asset> findAllAvailableDuring(
            @Param("exchange") Exchange exchange,
            @Param("quoteCurrencyCode") String quoteCurrencyCode,
            @Param("periodStart") Instant periodStart,
            @Param("periodEnd") Instant periodEnd
    );
}
