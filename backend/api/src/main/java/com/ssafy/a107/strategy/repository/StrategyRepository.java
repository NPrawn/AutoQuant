package com.ssafy.a107.strategy.repository;

import com.ssafy.a107.strategy.domain.Strategy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StrategyRepository extends JpaRepository<Strategy, Long> {

    List<Strategy> findAllByMemberMemberIdAndDeletedAtIsNull(UUID memberId);

    Optional<Strategy> findByStrategyIdAndMemberMemberIdAndDeletedAtIsNull(Long strategyId, UUID memberId);

    /* 복제 대상은 내 전략이거나 공개된 전략이다. 소유와 공개 판단은 서비스가 한다. */
    Optional<Strategy> findByStrategyIdAndDeletedAtIsNull(Long strategyId);
}
