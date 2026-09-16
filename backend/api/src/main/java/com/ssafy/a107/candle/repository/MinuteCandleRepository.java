package com.ssafy.a107.candle.repository;

import com.ssafy.a107.candle.domain.MinuteCandle;
import com.ssafy.a107.candle.domain.MinuteCandleId;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 확정 봉 쓰기는 Go 전용이므로 조회 메서드만 노출한다.
public interface MinuteCandleRepository extends Repository<MinuteCandle, MinuteCandleId> {

    List<MinuteCandle> findByIdAssetIdAndIdIntervalMinutesOrderByIdOpenTimeDesc(
            UUID assetId, short intervalMinutes, Pageable pageable
    );

    /*
     * 보유 구간의 양 끝. PK 가 (asset_id, interval_minutes, open_time) 이라 종목별 인덱스를 훑어
     * 한 건만 집어 온다. 집계 대신 양 끝을 따로 읽는 이유는 종목 수가 늘어도 비용이 같기 때문이다.
     */
    Optional<MinuteCandle> findFirstByIdIntervalMinutesAndIdAssetIdInOrderByIdOpenTimeAsc(
            short intervalMinutes, Collection<UUID> assetIds
    );

    Optional<MinuteCandle> findFirstByIdIntervalMinutesAndIdAssetIdInOrderByIdOpenTimeDesc(
            short intervalMinutes, Collection<UUID> assetIds
    );
}
