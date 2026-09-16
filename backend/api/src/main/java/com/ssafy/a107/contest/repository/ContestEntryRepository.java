package com.ssafy.a107.contest.repository;

import com.ssafy.a107.contest.domain.ContestEntry;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContestEntryRepository extends JpaRepository<ContestEntry, UUID> {

    @EntityGraph(attributePaths = "strategy")
    Optional<ContestEntry> findByRoundRoundIdAndMemberMemberId(UUID roundId, UUID memberId);

    @EntityGraph(attributePaths = "strategy")
    List<ContestEntry> findAllByMemberMemberId(UUID memberId);

    List<ContestEntry> findAllByRoundRoundId(UUID roundId);

    long countByRoundRoundId(UUID roundId);
}
