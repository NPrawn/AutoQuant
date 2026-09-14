package com.ssafy.a107.trading.backtest.engine.universe;

import com.ssafy.a107.asset.domain.Asset;
import com.ssafy.a107.asset.domain.Exchange;
import com.ssafy.a107.asset.service.CollectedAssetReadService;
import com.ssafy.a107.trading.backtest.domain.BacktestRun;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Component
public class BacktestUniverseResolver {

    private final CollectedAssetReadService collectedAssetReadService;

    public BacktestUniverseResolver(CollectedAssetReadService collectedAssetReadService) {
        this.collectedAssetReadService = collectedAssetReadService;
    }

    public List<Asset> resolve(
            BacktestRun backtestRun,
            Instant evaluationAt
    ) {
        Objects.requireNonNull(backtestRun);
        Objects.requireNonNull(evaluationAt);

        if (evaluationAt.isBefore(backtestRun.getPeriodStart())
                || !evaluationAt.isBefore(backtestRun.getPeriodEnd())) {
            throw new IllegalArgumentException(
                    "Universe evaluation time must be within backtest period"
            );
        }

        return List.copyOf(collectedAssetReadService.findAvailableAt(
                Exchange.UPBIT,
                backtestRun.getQuoteCurrency(),
                evaluationAt
        ));
    }

    public List<Asset> resolveCandidates(BacktestRun backtestRun) {
        Objects.requireNonNull(backtestRun);

        return List.copyOf(collectedAssetReadService.findAvailableDuring(
                Exchange.UPBIT,
                backtestRun.getQuoteCurrency(),
                backtestRun.getPeriodStart(),
                backtestRun.getPeriodEnd()
        ));
    }
}
