package com.ssafy.a107.contest.repository;

import com.ssafy.a107.contest.domain.MarketDataset;
import com.ssafy.a107.contest.domain.MarketDatasetKind;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MarketDatasetRepository extends JpaRepository<MarketDataset, UUID> {

    List<MarketDataset> findAllByRoundRoundIdOrderByKindAsc(UUID roundId);

    Optional<MarketDataset> findByRoundRoundIdAndKind(UUID roundId, MarketDatasetKind kind);
}
