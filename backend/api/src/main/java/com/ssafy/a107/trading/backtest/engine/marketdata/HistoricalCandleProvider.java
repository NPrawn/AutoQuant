package com.ssafy.a107.trading.backtest.engine.marketdata;

import com.ssafy.a107.candle.domain.MinuteCandle;
import com.ssafy.a107.strategy.domain.dsl.Timeframe;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface HistoricalCandleProvider {

    Map<UUID, List<MinuteCandle>> load(
            Collection<UUID> assetIds,
            Timeframe timeframe,
            Instant fromInclusive,
            Instant toExclusive
    );
}
