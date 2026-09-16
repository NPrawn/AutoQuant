package com.ssafy.a107.contest.repository;

import com.ssafy.a107.contest.domain.ContestPhase;
import com.ssafy.a107.contest.domain.ContestRound;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ContestRoundRepository extends JpaRepository<ContestRound, UUID> {

    List<ContestRound> findAllByOrderBySubmissionStartsAtDesc();

    List<ContestRound> findAllByPhaseAndSubmissionEndsAtLessThanEqual(ContestPhase phase, Instant at);

    List<ContestRound> findAllByPhase(ContestPhase phase);
}
