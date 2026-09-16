package com.ssafy.a107.contest.repository;

import com.ssafy.a107.contest.domain.ContestCandle;
import com.ssafy.a107.contest.domain.ContestCandleId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ContestCandleRepository extends JpaRepository<ContestCandle, ContestCandleId> {

    List<ContestCandle> findAllByIdDatasetIdAndIdAssetIdInAndIdIntervalMinutesAndIdOpenTimeGreaterThanEqualAndIdOpenTimeLessThanOrderByIdAssetIdAscIdOpenTimeAsc(
            UUID datasetId,
            Collection<UUID> assetIds,
            short intervalMinutes,
            Instant fromInclusive,
            Instant toExclusive
    );
}
