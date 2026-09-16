package com.ssafy.a107.contest.repository;

import com.ssafy.a107.contest.domain.ContestResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    /*
     * 랭킹 화면(목업 06)이 쓰는 조회다. 순위가 있는 제출만 보여 주고, 국면 필터는 그 국면의 낙폭 백분위가
     * 세 국면 중 가장 높은 제출을 남긴다. 정렬 기준이 둘뿐이라 쿼리 하나에 담고 동점은 순위로 가른다.
     */
    @Query(
            value = """
                    select new com.ssafy.a107.contest.repository.ContestRankingRow(
                        result.ranking, strategy.strategyId, strategy.name, member.displayName, strategy.published,
                        result.totalScore, result.returnScore, result.stabilityScore,
                        result.bullMddPercentile, result.bearMddPercentile, result.sidewaysMddPercentile,
                        member.memberId
                    )
                    from ContestEntry entry
                    join entry.strategy strategy
                    join entry.member member
                    join ContestResult result on result.entryId = entry.entryId
                    where entry.round.roundId = :roundId
                      and result.ranking is not null
                      and (
                        :keyword is null
                        or lower(strategy.name) like :keyword
                        or lower(member.displayName) like :keyword
                      )
                      and (
                        :bull = false
                        or (result.bullMddPercentile >= result.bearMddPercentile
                            and result.bullMddPercentile >= result.sidewaysMddPercentile)
                      )
                      and (
                        :bear = false
                        or (result.bearMddPercentile >= result.bullMddPercentile
                            and result.bearMddPercentile >= result.sidewaysMddPercentile)
                      )
                      and (
                        :sideways = false
                        or (result.sidewaysMddPercentile >= result.bullMddPercentile
                            and result.sidewaysMddPercentile >= result.bearMddPercentile)
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
                      and (
                        :keyword is null
                        or lower(strategy.name) like :keyword
                        or lower(member.displayName) like :keyword
                      )
                      and (
                        :bull = false
                        or (result.bullMddPercentile >= result.bearMddPercentile
                            and result.bullMddPercentile >= result.sidewaysMddPercentile)
                      )
                      and (
                        :bear = false
                        or (result.bearMddPercentile >= result.bullMddPercentile
                            and result.bearMddPercentile >= result.sidewaysMddPercentile)
                      )
                      and (
                        :sideways = false
                        or (result.sidewaysMddPercentile >= result.bullMddPercentile
                            and result.sidewaysMddPercentile >= result.bearMddPercentile)
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
