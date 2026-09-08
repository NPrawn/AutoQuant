package com.ssafy.a107.trading.backtest.repository;

import com.ssafy.a107.trading.backtest.domain.BacktestResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BacktestResultRepository extends JpaRepository<BacktestResult, UUID> {
}
