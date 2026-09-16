package com.ssafy.a107.candle.service;

import com.ssafy.a107.asset.service.CollectedAssetReadService;
import com.ssafy.a107.candle.domain.MinuteCandleCoverage;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/*
 * 화면이 고를 수 있는 기간의 경계를 만든다. 대상은 백테스트 후보군과 같은 수집 종목이어야 한다.
 * 수집하지 않는 종목의 봉까지 세면 화면은 실행할 수 있다고 알리지만 엔진은 그 구간을 쓰지 않는다.
 */
@Service
public class CandleCoverageFacade {

    private final CollectedAssetReadService collectedAssetReadService;
    private final CandleCoverageReadService candleCoverageReadService;

    public CandleCoverageFacade(
            CollectedAssetReadService collectedAssetReadService,
            CandleCoverageReadService candleCoverageReadService
    ) {
        this.collectedAssetReadService = collectedAssetReadService;
        this.candleCoverageReadService = candleCoverageReadService;
    }

    public MinuteCandleCoverage findCoverage(int intervalMinutes) {
        List<UUID> assetIds = collectedAssetReadService.findCollectedAssetIds();
        return candleCoverageReadService.findCoverage(intervalMinutes, assetIds);
    }
}
