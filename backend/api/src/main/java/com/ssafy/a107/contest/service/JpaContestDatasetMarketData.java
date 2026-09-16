package com.ssafy.a107.contest.service;

import com.ssafy.a107.candle.domain.MinuteCandle;
import com.ssafy.a107.candle.domain.MinuteCandleId;
import com.ssafy.a107.contest.domain.ContestCandle;
import com.ssafy.a107.contest.domain.MarketDatasetAsset;
import com.ssafy.a107.contest.repository.ContestCandleRepository;
import com.ssafy.a107.contest.repository.MarketDatasetAssetRepository;
import com.ssafy.a107.strategy.domain.dsl.Timeframe;
import com.ssafy.a107.trading.backtest.engine.marketdata.ContestDatasetMarketData;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class JpaContestDatasetMarketData implements ContestDatasetMarketData {

    private final MarketDatasetAssetRepository marketDatasetAssetRepository;
    private final ContestCandleRepository contestCandleRepository;

    public JpaContestDatasetMarketData(
            MarketDatasetAssetRepository marketDatasetAssetRepository,
            ContestCandleRepository contestCandleRepository
    ) {
        this.marketDatasetAssetRepository = Objects.requireNonNull(marketDatasetAssetRepository);
        this.contestCandleRepository = Objects.requireNonNull(contestCandleRepository);
    }

    @Override
    public List<UUID> findAssetIds(UUID datasetId) {
        Objects.requireNonNull(datasetId);

        return marketDatasetAssetRepository.findAllByIdDatasetIdOrderByIdAssetIdAsc(datasetId)
                .stream()
                .map(MarketDatasetAsset::getId)
                .map(id -> id.getAssetId())
                .toList();
    }

    @Override
    public Map<UUID, List<MinuteCandle>> loadCandles(
            UUID datasetId,
            Collection<UUID> assetIds,
            Timeframe timeframe,
            Instant fromInclusive,
            Instant toExclusive
    ) {
        Objects.requireNonNull(datasetId);
        Objects.requireNonNull(assetIds);
        Objects.requireNonNull(timeframe);
        Objects.requireNonNull(fromInclusive);
        Objects.requireNonNull(toExclusive);

        if (!fromInclusive.isBefore(toExclusive)) {
            throw new IllegalArgumentException("Candle range start must be before end");
        }

        List<UUID> orderedAssetIds = assetIds.stream()
                .map(Objects::requireNonNull)
                .distinct()
                .sorted()
                .toList();

        if (orderedAssetIds.isEmpty()) {
            return Map.of();
        }

        Map<UUID, List<MinuteCandle>> candlesByAssetId = new LinkedHashMap<>();
        for (UUID assetId : orderedAssetIds) {
            candlesByAssetId.put(assetId, new ArrayList<>());
        }

        List<ContestCandle> candles = contestCandleRepository
                .findAllByIdDatasetIdAndIdAssetIdInAndIdIntervalMinutesAndIdOpenTimeGreaterThanEqualAndIdOpenTimeLessThanOrderByIdAssetIdAscIdOpenTimeAsc(
                        datasetId,
                        orderedAssetIds,
                        (short) timeframe.getIntervalMinutes(),
                        fromInclusive,
                        toExclusive
                );

        for (ContestCandle candle : candles) {
            candlesByAssetId.get(candle.getId().getAssetId()).add(toMinuteCandle(candle));
        }

        Map<UUID, List<MinuteCandle>> immutableCandlesByAssetId = new LinkedHashMap<>();
        for (Map.Entry<UUID, List<MinuteCandle>> entry : candlesByAssetId.entrySet()) {
            immutableCandlesByAssetId.put(entry.getKey(), List.copyOf(entry.getValue()));
        }

        return Collections.unmodifiableMap(immutableCandlesByAssetId);
    }

    /*
     * 엔진 입력 타입이 MinuteCandle 이라 대회 캔들을 같은 타입으로 옮긴다. 저장하지 않는 값이다 (ADR-0014).
     * 마지막 체결 시각은 대회 데이터에 없어 봉의 마지막 초로 둔다.
     */
    private MinuteCandle toMinuteCandle(ContestCandle candle) {
        int intervalMinutes = candle.getId().getIntervalMinutes();
        Instant openTime = candle.getId().getOpenTime();

        return MinuteCandle.detached(
                new MinuteCandleId(candle.getId().getAssetId(), intervalMinutes, openTime),
                candle.getOpenPrice(),
                candle.getHighPrice(),
                candle.getLowPrice(),
                candle.getClosePrice(),
                candle.getTradeVolume(),
                candle.getTradeAmount(),
                openTime.plus(Duration.ofMinutes(intervalMinutes)).minusSeconds(1)
        );
    }
}
