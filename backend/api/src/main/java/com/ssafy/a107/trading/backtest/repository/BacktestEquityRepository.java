package com.ssafy.a107.trading.backtest.repository;

import com.ssafy.a107.trading.backtest.domain.BacktestEquity;
import com.ssafy.a107.trading.backtest.domain.BacktestEquityId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface BacktestEquityRepository extends JpaRepository<BacktestEquity, BacktestEquityId> {

    Page<BacktestEquity> findAllByBacktestRunBacktestIdOrderByIdValuationAtAsc(
            UUID backtestId,
            Pageable pageable
    );

    // 평균 투자 비중이다. 대회 채점의 실격 규칙이 쓴다 (ADR-0003).
    @Query("""
            select  avg(1 - equity.cashBalance / equity.totalAssetValue)
            from BacktestEquity equity
            where equity.backtestRun.backtestId = :backtestId
              and equity.totalAssetValue > 0
            """)
    Optional<BigDecimal> findAverageExposure(@Param("backtestId") UUID backtestId);
}
