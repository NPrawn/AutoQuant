package com.ssafy.a107.trading.backtest.repository;

import com.ssafy.a107.candle.domain.MinuteCandle;
import com.ssafy.a107.candle.domain.MinuteCandleId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface MinuteCandleRepository extends JpaRepository<MinuteCandle, MinuteCandleId> {

    List<MinuteCandle> findAllByIdAssetIdInAndIdIntervalMinutesAndIdOpenTimeGreaterThanEqualAndIdOpenTimeLessThanOrderByIdAssetIdAscIdOpenTimeAsc(
            Collection<UUID> assetIds,
            short intervalMinutes,
            Instant fromInclusive,
            Instant toExclusive
    );
}
