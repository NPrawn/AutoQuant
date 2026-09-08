package com.ssafy.a107.trading.backtest.repository;

import com.ssafy.a107.trading.backtest.domain.BacktestTrade;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BacktestTradeRepository extends JpaRepository<BacktestTrade, UUID> {

    @EntityGraph(attributePaths = "asset")
    Page<BacktestTrade> findAllByBacktestRunBacktestIdOrderByFilledAtAscTradeIdAsc(
            UUID backtestId,
            Pageable pageable
    );
}
