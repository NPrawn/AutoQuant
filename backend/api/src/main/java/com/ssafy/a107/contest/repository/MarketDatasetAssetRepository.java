package com.ssafy.a107.contest.repository;

import com.ssafy.a107.contest.domain.MarketDatasetAsset;
import com.ssafy.a107.contest.domain.MarketDatasetAssetId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MarketDatasetAssetRepository extends JpaRepository<MarketDatasetAsset, MarketDatasetAssetId> {

    List<MarketDatasetAsset> findAllByIdDatasetIdOrderByIdAssetIdAsc(UUID datasetId);
}
