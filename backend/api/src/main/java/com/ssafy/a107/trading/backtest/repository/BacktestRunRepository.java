package com.ssafy.a107.trading.backtest.repository;

import com.ssafy.a107.trading.backtest.domain.BacktestRun;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface BacktestRunRepository extends JpaRepository<BacktestRun, UUID> {

    @EntityGraph(attributePaths = "strategy")
    /* 대회 백테스트는 연습 화면에서 열지 않는다. 체결과 자산 추이로 채점용 데이터의 가격 경로가 드러난다 (ADR-0010). */
    Optional<BacktestRun> findByBacktestIdAndMemberMemberIdAndContestDatasetIdIsNull(UUID backtestId, UUID memberId);

    // 대회 채점용 실행은 사용자 목록에 넣지 않는다 (ADR-0014).
    @EntityGraph(attributePaths = "strategy")
    Page<BacktestRun> findAllByMemberMemberIdAndContestDatasetIdIsNullOrderByQueuedAtDesc(
            UUID memberId,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "strategy")
    @Query("""
            select  backtestRun
            from BacktestRun backtestRun
            where backtestRun.backtestId = :backtestId
            """)
    Optional<BacktestRun> findByBacktestIdForUpdate(@Param("backtestId") UUID backtestId);

    @EntityGraph(attributePaths = "strategy")
    Optional<BacktestRun> findByBacktestId(UUID backtestId);
}
