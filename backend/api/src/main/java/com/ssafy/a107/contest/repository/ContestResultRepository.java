package com.ssafy.a107.contest.repository;

import com.ssafy.a107.contest.domain.ContestResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContestResultRepository extends JpaRepository<ContestResult, UUID> {

    @Query("""
            select  result
            from ContestResult result
            where result.entryId in (
                select entry.entryId from ContestEntry entry where entry.round.roundId = :roundId
            )
            """)
    List<ContestResult> findAllByRoundId(@Param("roundId") UUID roundId);

    Optional<ContestResult> findByEntryId(UUID entryId);
}
