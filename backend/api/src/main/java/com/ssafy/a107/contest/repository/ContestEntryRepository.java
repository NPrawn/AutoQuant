package com.ssafy.a107.contest.repository;

import com.ssafy.a107.contest.domain.ContestEntry;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContestEntryRepository extends JpaRepository<ContestEntry, UUID> {

    /* 제출마다 행이 쌓이므로 "내 제출"은 여럿이다. 한 건만 보여 주는 화면은 가장 최근 것을 쓴다. */
    @EntityGraph(attributePaths = {"strategy", "round"})
    Optional<ContestEntry> findFirstByRoundRoundIdAndMemberMemberIdOrderBySubmittedAtDesc(
            UUID roundId, UUID memberId);

    @EntityGraph(attributePaths = {"strategy", "round"})
    List<ContestEntry> findAllByMemberMemberId(UUID memberId);

    List<ContestEntry> findAllByRoundRoundId(UUID roundId);

    /* 제출 수다. 제출마다 행이 쌓이므로 참가자 수가 아니다. */
    long countByRoundRoundId(UUID roundId);

    /* 목록은 회차가 여럿이라 한 번에 센다. 회차마다 세면 질의가 회차 수만큼 는다. 이것도 제출 수다. */
    @Query("""
            select new com.ssafy.a107.contest.repository.ContestRoundEntryCount(
                entry.round.roundId, count(entry)
            )
            from ContestEntry entry
            group by entry.round.roundId
            """)
    List<ContestRoundEntryCount> countByRound();
}
