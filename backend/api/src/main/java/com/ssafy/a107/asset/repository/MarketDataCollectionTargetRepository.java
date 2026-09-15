package com.ssafy.a107.asset.repository;

import com.ssafy.a107.asset.domain.MarketDataCollectionStatus;
import com.ssafy.a107.asset.domain.MarketDataCollectionTarget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MarketDataCollectionTargetRepository extends JpaRepository<MarketDataCollectionTarget, UUID> {

    List<MarketDataCollectionTarget> findByCollectionStatus(MarketDataCollectionStatus collectionStatus);
}
