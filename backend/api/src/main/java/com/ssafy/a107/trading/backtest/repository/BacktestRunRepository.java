package com.ssafy.a107.trading.backtest.repository;

import com.ssafy.a107.trading.backtest.domain.BacktestRun;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BacktestRunRepository extends JpaRepository<BacktestRun, UUID> {

    @EntityGraph(attributePaths = "strategy")
    Optional<BacktestRun> findByBacktestIdAndMemberMemberId(UUID backtestId, UUID memberId);

    @EntityGraph(attributePaths = "strategy")
    Page<BacktestRun> findAllByMemberMemberIdOrderByQueuedAtDesc(UUID memberId, Pageable pageable);
}
