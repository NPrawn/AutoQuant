package com.ssafy.a107.candle.service;

import com.ssafy.a107.candle.domain.MinuteCandle;
import com.ssafy.a107.candle.domain.MinuteCandleCoverage;
import com.ssafy.a107.candle.domain.MinuteCandleId;
import com.ssafy.a107.candle.repository.MinuteCandleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/* 보유 구간의 DB 읽기 전용 트랜잭션만 맡는다. 대상 종목을 고르는 일은 Facade 가 한다. */
@Service
@Transactional(readOnly = true)
public class CandleCoverageReadService {

    private final MinuteCandleRepository minuteCandleRepository;

    public CandleCoverageReadService(MinuteCandleRepository minuteCandleRepository) {
        this.minuteCandleRepository = minuteCandleRepository;
    }

    public MinuteCandleCoverage findCoverage(int intervalMinutes, List<UUID> assetIds) {
        // 빈 목록을 in 절에 넣으면 DB 마다 동작이 달라 질의 자체를 하지 않는다.
        if (assetIds.isEmpty()) {
            return MinuteCandleCoverage.empty();
        }

        short interval = (short) intervalMinutes;
        Instant oldest = openTimeOf(minuteCandleRepository
                .findFirstByIdIntervalMinutesAndIdAssetIdInOrderByIdOpenTimeAsc(interval, assetIds));
        if (oldest == null) {
            return MinuteCandleCoverage.empty();
        }

        Instant newest = openTimeOf(minuteCandleRepository
                .findFirstByIdIntervalMinutesAndIdAssetIdInOrderByIdOpenTimeDesc(interval, assetIds));
        return new MinuteCandleCoverage(oldest, newest);
    }

    private static Instant openTimeOf(Optional<MinuteCandle> candle) {
        return candle.map(MinuteCandle::getId).map(MinuteCandleId::getOpenTime).orElse(null);
    }
}
