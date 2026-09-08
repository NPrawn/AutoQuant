package com.ssafy.a107.trading.backtest.repository;

import com.ssafy.a107.trading.backtest.domain.BacktestEquity;
import com.ssafy.a107.trading.backtest.domain.BacktestEquityId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BacktestEquityRepository extends JpaRepository<BacktestEquity, BacktestEquityId> {

    Page<BacktestEquity> findAllByBacktestRunBacktestIdOrderByIdValuationAtAsc(
            UUID backtestId,
            Pageable pageable
    );
}
