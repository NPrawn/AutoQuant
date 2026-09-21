package com.ssafy.a107.strategy.repository;

import com.ssafy.a107.strategy.domain.Strategy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
