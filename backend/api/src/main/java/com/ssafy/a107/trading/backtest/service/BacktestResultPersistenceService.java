package com.ssafy.a107.trading.backtest.service;

import com.ssafy.a107.asset.domain.Asset;
import com.ssafy.a107.trading.backtest.domain.BacktestEquity;
import com.ssafy.a107.trading.backtest.domain.BacktestEquityId;
import com.ssafy.a107.trading.backtest.domain.BacktestResult;
import com.ssafy.a107.trading.backtest.domain.BacktestRun;
import com.ssafy.a107.trading.backtest.domain.BacktestTrade;
import com.ssafy.a107.trading.backtest.domain.BacktestUniverseAsset;
import com.ssafy.a107.trading.backtest.engine.execution.VirtualFill;
import com.ssafy.a107.trading.backtest.engine.performance.EquitySnapshot;
import com.ssafy.a107.trading.backtest.engine.performance.PerformanceReport;
import com.ssafy.a107.trading.backtest.repository.BacktestEquityRepository;
import com.ssafy.a107.trading.backtest.repository.BacktestResultRepository;
import com.ssafy.a107.trading.backtest.repository.BacktestTradeRepository;
import com.ssafy.a107.trading.backtest.repository.BacktestUniverseAssetBatchRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@Transactional
public class BacktestResultPersistenceService {

    private final BacktestResultRepository backtestResultRepository;
    private final BacktestTradeRepository backtestTradeRepository;
    private final BacktestEquityRepository backtestEquityRepository;
    private final BacktestUniverseAssetBatchRepository backtestUniverseAssetBatchRepository;

    public BacktestResultPersistenceService(
            BacktestResultRepository backtestResultRepository,
            BacktestTradeRepository backtestTradeRepository,
            BacktestEquityRepository backtestEquityRepository,
            BacktestUniverseAssetBatchRepository backtestUniverseAssetBatchRepository
    ) {
        this.backtestResultRepository = backtestResultRepository;
        this.backtestTradeRepository = backtestTradeRepository;
        this.backtestEquityRepository = backtestEquityRepository;
        this.backtestUniverseAssetBatchRepository = backtestUniverseAssetBatchRepository;
    }

    public BacktestResult persist(
            BacktestRun backtestRun,
            PerformanceReport performanceReport,
            List<VirtualFill> fills,
            Map<UUID, Asset> assets
    ) {
        return persist(
                backtestRun,
                performanceReport,
                fills,
                assets,
                List.of()
        );
    }

    public BacktestResult persist(
            BacktestRun backtestRun,
            PerformanceReport performanceReport,
            List<VirtualFill> fills,
            Map<UUID, Asset> assets,
            List<BacktestUniverseAsset> universeAssets
    ) {
        Objects.requireNonNull(backtestRun);
        Objects.requireNonNull(performanceReport);
        Objects.requireNonNull(fills);
        Objects.requireNonNull(assets);
        Objects.requireNonNull(universeAssets);

        List<BacktestTrade> trades = fills.stream()
                .map(fill -> toBacktestTrade(backtestRun, fill, assets))
                .toList();
        List<BacktestEquity> equities = performanceReport.equitySnapshots()
                .stream()
                .map(snapshot -> toBacktestEquity(backtestRun, snapshot))
                .toList();

        BacktestResult backtestResult = backtestResultRepository.save(
                toBacktestResult(backtestRun, performanceReport)
        );
        backtestTradeRepository.saveAll(trades);
        backtestEquityRepository.saveAll(equities);
        backtestUniverseAssetBatchRepository.insertAll(universeAssets);

        return backtestResult;
    }

    private BacktestResult toBacktestResult(
            BacktestRun backtestRun,
            PerformanceReport performanceReport
    ) {
        return new BacktestResult(
                backtestRun,
                performanceReport.finalAssetValue(),
                performanceReport.cumulativeReturn(),
                performanceReport.cagr(),
                performanceReport.maximumDrawdown(),
                performanceReport.sharpeRatio().orElse(null),
                performanceReport.volatility().orElse(null),
                performanceReport.winRate().orElse(null),
                performanceReport.tradeStatistics().tradeCount(),
                performanceReport.tradeStatistics().totalFee(),
                performanceReport.benchmarkReturn().orElse(null)
        );
    }

    private BacktestTrade toBacktestTrade(
            BacktestRun backtestRun,
            VirtualFill fill,
            Map<UUID, Asset> assets
    ) {
        if (fill == null) {
            throw new IllegalArgumentException(
                    "Virtual fill must not be null"
            );
        }

        Asset asset = assets.get(fill.order().assetId());
        if (asset == null) {
            throw new IllegalArgumentException(
                    "Asset is required for every virtual fill"
            );
        }

        return new BacktestTrade(
                backtestRun,
                asset,
                fill.order().positionSide(),
                fill.order().side(),
                fill.order().signalAt(),
                fill.order().filledAt(),
                fill.executedPrice(),
                fill.quantity(),
                fill.notional(),
                fill.feeAmount(),
                fill.slippageAmount(),
                null
        );
    }

    private BacktestEquity toBacktestEquity(
            BacktestRun backtestRun,
            EquitySnapshot snapshot
    ) {
        if (snapshot == null) {
            throw new IllegalArgumentException(
                    "Equity snapshot must not be null"
            );
        }

        return new BacktestEquity(
                new BacktestEquityId(
                        backtestRun.getBacktestId(),
                        snapshot.valuationAt()
                ),
                backtestRun,
                snapshot.totalAssetValue(),
                snapshot.cashBalance(),
                snapshot.cumulativeReturn(),
                snapshot.drawdownRate()
        );
    }
}
