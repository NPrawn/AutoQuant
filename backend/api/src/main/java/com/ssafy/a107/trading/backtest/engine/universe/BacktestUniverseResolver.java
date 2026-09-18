package com.ssafy.a107.trading.backtest.engine.universe;

import com.ssafy.a107.asset.domain.Asset;
import com.ssafy.a107.asset.domain.Exchange;
import com.ssafy.a107.asset.repository.AssetRepository;
import com.ssafy.a107.asset.service.CollectedAssetReadService;
import com.ssafy.a107.trading.backtest.domain.BacktestRun;
import com.ssafy.a107.trading.backtest.engine.marketdata.ContestDatasetMarketData;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Component
public class BacktestUniverseResolver {

    private final CollectedAssetReadService collectedAssetReadService;
    private final ContestDatasetMarketData contestDatasetMarketData;
    private final AssetRepository assetRepository;

    public BacktestUniverseResolver(
            CollectedAssetReadService collectedAssetReadService,
            ContestDatasetMarketData contestDatasetMarketData,
            AssetRepository assetRepository
    ) {
        this.collectedAssetReadService = collectedAssetReadService;
        this.contestDatasetMarketData = contestDatasetMarketData;
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

        if (backtestRun.isContest()) {
            return resolveDatasetAssets(backtestRun);
        }

        return List.copyOf(collectedAssetReadService.findAvailableAt(
                Exchange.UPBIT,
                backtestRun.getQuoteCurrency(),
                evaluationAt
        ));
    }

    public List<Asset> resolveCandidates(BacktestRun backtestRun) {
        Objects.requireNonNull(backtestRun);

        if (backtestRun.isContest()) {
            return resolveDatasetAssets(backtestRun);
        }

        return List.copyOf(collectedAssetReadService.findAvailableDuring(
                Exchange.UPBIT,
                backtestRun.getQuoteCurrency(),
                backtestRun.getPeriodStart(),
                backtestRun.getPeriodEnd()
        ));
    }

    /*
     * 대회 백테스트의 후보군은 데이터셋 종목 전체다. 가상 날짜에 실제 거래지원 기간과 수집 상태를 적용할 이유가 없다 (ADR-0014).
     */
    private List<Asset> resolveDatasetAssets(BacktestRun backtestRun) {
        List<UUID> assetIds = contestDatasetMarketData.findAssetIds(backtestRun.getContestDatasetId());

        Map<UUID, Asset> assetsById = new LinkedHashMap<>();
        for (Asset asset : assetRepository.findAllById(assetIds)) {
            assetsById.put(asset.getAssetId(), asset);
        }

        return assetIds.stream()
                .map(assetId -> {
                    Asset asset = assetsById.get(assetId);
                    if (asset == null) {
                        throw new IllegalStateException("Contest dataset asset is missing: " + assetId);
                    }
                    return asset;
                })
                .toList();
    }
}
