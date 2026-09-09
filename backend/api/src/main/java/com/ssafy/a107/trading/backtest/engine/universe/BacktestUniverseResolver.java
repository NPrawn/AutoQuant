package com.ssafy.a107.trading.backtest.engine.universe;

import com.ssafy.a107.asset.domain.Asset;
import com.ssafy.a107.asset.domain.Exchange;
import com.ssafy.a107.asset.repository.AssetRepository;
import com.ssafy.a107.trading.backtest.domain.BacktestRun;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Component
public class BacktestUniverseResolver {

    private final AssetRepository assetRepository;

    public BacktestUniverseResolver(AssetRepository assetRepository) {
        this.assetRepository = assetRepository;
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

        return List.copyOf(assetRepository.findAllAvailableAt(
                Exchange.UPBIT,
                backtestRun.getQuoteCurrency(),
                evaluationAt
        ));
    }

    public List<Asset> resolveCandidates(BacktestRun backtestRun) {
        Objects.requireNonNull(backtestRun);

        return List.copyOf(assetRepository.findAllAvailableDuring(
                Exchange.UPBIT,
                backtestRun.getQuoteCurrency(),
                backtestRun.getPeriodStart(),
                backtestRun.getPeriodEnd()
        ));
    }
}
