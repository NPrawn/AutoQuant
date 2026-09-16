package com.ssafy.a107.trading.backtest.engine.marketdata;

import com.ssafy.a107.candle.domain.MinuteCandle;
import com.ssafy.a107.strategy.domain.dsl.Timeframe;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/*
 * 대회 데이터셋의 캔들과 종목을 읽는다. trading 이 contest 를 직접 참조하지 않도록 인터페이스만 여기 두고
 * contest 패키지가 구현한다 (ADR-0014).
 */
public interface ContestDatasetMarketData {

    List<UUID> findAssetIds(UUID datasetId);

    Map<UUID, List<MinuteCandle>> loadCandles(
            UUID datasetId,
            Collection<UUID> assetIds,
            Timeframe timeframe,
            Instant fromInclusive,
            Instant toExclusive
    );
}
