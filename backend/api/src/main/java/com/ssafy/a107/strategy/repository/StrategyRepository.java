package com.ssafy.a107.strategy.repository;

import com.ssafy.a107.strategy.domain.Strategy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StrategyRepository extends JpaRepository<Strategy, Long> {

    List<Strategy> findAllByMemberMemberIdAndDeletedAtIsNull(UUID memberId);

    Optional<Strategy> findByStrategyIdAndMemberMemberIdAndDeletedAtIsNull(Long strategyId, UUID memberId);

    /* 복제 대상은 내 전략이거나 공개된 전략이다. 소유와 공개 판단은 서비스가 한다. */
    Optional<Strategy> findByStrategyIdAndDeletedAtIsNull(Long strategyId);

    /* 뿌리 하나에 속한 판들의 최대 판 번호. 뿌리 자신도 세고, 삭제된 판도 번호를 차지하므로 함께 센다. */
    @Query("""
            select coalesce(max(s.versionNo), 0)
            from Strategy s
            where s.strategyId = :rootStrategyId or s.rootStrategyId = :rootStrategyId
            """)
    int findLatestVersionNo(@Param("rootStrategyId") Long rootStrategyId);

    /* 뿌리 하나에 속한 내 활성 판 전체. 삭제는 전략 단위라 판을 모두 지운다. */
    @Query("""
            select s
            from Strategy s
            where (s.strategyId = :rootStrategyId or s.rootStrategyId = :rootStrategyId)
              and s.member.memberId = :memberId
              and s.deletedAt is null
            """)
    List<Strategy> findAllActiveByRoot(@Param("rootStrategyId") Long rootStrategyId, @Param("memberId") UUID memberId);

    /*
     * 뿌리 여러 개에 속한 판 전체. 순위표가 "뿌리 기준 최신 이름"을 붙일 때 쓴다.
     *
     * 판마다 행이 있어 뿌리 하나에 몇 행뿐이라 그대로 읽고 최신 판을 호출한 쪽이 고른다. 상관 서브쿼리로
     * 최대 판 번호를 찾는 것보다 질의가 단순하고, 뿌리 수가 한 페이지 분량이라 비용도 같다.
     */
    @Query("""
            select s
            from Strategy s
            where coalesce(s.rootStrategyId, s.strategyId) in :rootStrategyIds
              and s.deletedAt is null
            """)
    List<Strategy> findAllByRootIn(@Param("rootStrategyIds") Collection<Long> rootStrategyIds);
}
