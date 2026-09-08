package com.ssafy.a107.candle.repository;

import com.ssafy.a107.candle.domain.MinuteCandle;
import com.ssafy.a107.candle.domain.MinuteCandleId;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.UUID;

// 확정 봉 쓰기는 Go 전용이므로 조회 메서드만 노출한다.
public interface MinuteCandleRepository extends Repository<MinuteCandle, MinuteCandleId> {

    List<MinuteCandle> findByIdAssetIdAndIdIntervalMinutesOrderByIdOpenTimeDesc(
            UUID assetId, short intervalMinutes, Pageable pageable
    );
}
