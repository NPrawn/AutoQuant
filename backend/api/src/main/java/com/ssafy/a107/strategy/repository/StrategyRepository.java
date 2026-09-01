package com.ssafy.a107.strategy.repository;

import com.ssafy.a107.strategy.domain.Strategy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StrategyRepository extends JpaRepository<Strategy, Long> {

    List<Strategy> findAllByMemberMemberIdAndDeletedAtIsNull(UUID memberId);

    Optional<Strategy> findByStrategyIdAndMemberMemberIdAndDeletedAtIsNull(Long strategyId, UUID memberId);
}
