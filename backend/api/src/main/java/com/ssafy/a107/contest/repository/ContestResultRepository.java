package com.ssafy.a107.contest.repository;

import com.ssafy.a107.contest.domain.ContestResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
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

    List<ContestResult> findAllByEntryIdIn(Collection<UUID> entryIds);

    /* 회차마다 1위 한 줄. 동점이면 그중 하나가 온다. */
    @Query("""
            select new com.ssafy.a107.contest.repository.ContestRoundTopResult(
                entry.round.roundId, strategy.name, member.displayName, result.totalScore, member.memberId
            )
            from ContestEntry entry
            join entry.strategy strategy
            join entry.member member
            join ContestResult result on result.entryId = entry.entryId
            where result.ranking = 1
            """)
    List<ContestRoundTopResult> findTopResults();

    void deleteByEntryId(UUID entryId);

    /* 점수는 절대값이라 다시 계산해도 같지만, 순위는 참가자가 늘면 바뀌므로 회차 결과를 통째로 다시 쓴다. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from ContestResult result
            where result.entryId in (
                select entry.entryId from ContestEntry entry where entry.round.roundId = :roundId
            )
            """)
    void deleteAllByRoundId(@Param("roundId") UUID roundId);

    /*
     * 랭킹 화면(목업 06)이 쓰는 조회다. 순위가 있는 제출만 보여 주고, 국면 필터는 그 국면 점수가
     * 세 국면 중 가장 높은 제출을 남긴다. 정렬 기준이 둘뿐이라 쿼리 하나에 담고 동점은 순위로 가른다.
     * 검색은 전략 이름만 본다. 참가자 이름은 가려서 보여 주므로 이름으로 찾으면 가린 실명이 드러난다.
     */
    @Query(
            value = """
                    select new com.ssafy.a107.contest.repository.ContestRankingRow(
                        result.ranking, strategy.strategyId, strategy.name, member.displayName, strategy.published,
                        result.totalScore, result.returnScore, result.stabilityScore,
                        result.bullRegimeScore, result.bearRegimeScore, result.sidewaysRegimeScore,
                        member.memberId
                    )
                    from ContestEntry entry
                    join entry.strategy strategy
                    join entry.member member
                    join ContestResult result on result.entryId = entry.entryId
                    where entry.round.roundId = :roundId
                      and result.ranking is not null
                      and (:keyword is null or lower(strategy.name) like :keyword)
                      and (
                        :bull = false
                        or (result.bullRegimeScore >= result.bearRegimeScore
                            and result.bullRegimeScore >= result.sidewaysRegimeScore)
                      )
                      and (
                        :bear = false
                        or (result.bearRegimeScore >= result.bullRegimeScore
                            and result.bearRegimeScore >= result.sidewaysRegimeScore)
                      )
                      and (
                        :sideways = false
                        or (result.sidewaysRegimeScore >= result.bullRegimeScore
                            and result.sidewaysRegimeScore >= result.bearRegimeScore)
                      )
                    order by (case when :byStability = true then result.stabilityScore else result.totalScore end) desc,
                             result.ranking asc
                    """,
            countQuery = """
                    select count(result)
                    from ContestEntry entry
                    join entry.strategy strategy
                    join entry.member member
                    join ContestResult result on result.entryId = entry.entryId
                    where entry.round.roundId = :roundId
                      and result.ranking is not null
                      and (:keyword is null or lower(strategy.name) like :keyword)
                      and (
                        :bull = false
                        or (result.bullRegimeScore >= result.bearRegimeScore
                            and result.bullRegimeScore >= result.sidewaysRegimeScore)
                      )
                      and (
                        :bear = false
                        or (result.bearRegimeScore >= result.bullRegimeScore
                            and result.bearRegimeScore >= result.sidewaysRegimeScore)
                      )
                      and (
                        :sideways = false
                        or (result.sidewaysRegimeScore >= result.bullRegimeScore
                            and result.sidewaysRegimeScore >= result.bearRegimeScore)
                      )
                    """
    )
    Page<ContestRankingRow> findRankings(
            @Param("roundId") UUID roundId,
            @Param("keyword") String keyword,
            @Param("bull") boolean bull,
            @Param("bear") boolean bear,
            @Param("sideways") boolean sideways,
            @Param("byStability") boolean byStability,
            Pageable pageable
    );

    @Query("""
            select count(result)
            from ContestResult result
            where result.entryId in (
                select entry.entryId from ContestEntry entry where entry.round.roundId = :roundId
            )
            and result.ranking is null
            """)
    long countDisqualifiedByRoundId(@Param("roundId") UUID roundId);
}
