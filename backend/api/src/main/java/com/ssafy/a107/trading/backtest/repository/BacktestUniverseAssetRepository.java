package com.ssafy.a107.trading.backtest.repository;

import com.ssafy.a107.trading.backtest.domain.BacktestUniverseAsset;
import com.ssafy.a107.trading.backtest.domain.BacktestUniverseAssetId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BacktestUniverseAssetRepository
        extends JpaRepository<BacktestUniverseAsset, BacktestUniverseAssetId> {
}
