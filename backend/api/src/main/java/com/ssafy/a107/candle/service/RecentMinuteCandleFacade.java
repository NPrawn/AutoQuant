package com.ssafy.a107.candle.service;

import com.ssafy.a107.candle.domain.MinuteCandleSnapshot;
import com.ssafy.a107.candle.service.port.RecentMinuteCandleCache;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class RecentMinuteCandleFacade {

    private final RecentMinuteCandleCache cache;
    private final MinuteCandleReadService readService;

    public RecentMinuteCandleFacade(RecentMinuteCandleCache cache, MinuteCandleReadService readService) {
        this.cache = cache;
        this.readService = readService;
    }

    public List<MinuteCandleSnapshot> findRecent(UUID assetId, int intervalMinutes, int limit) {
        List<MinuteCandleSnapshot> cached = cache.findRecent(assetId, intervalMinutes, limit);
        if (cached.size() == limit) {
            return cached;
        }

        // 짧은 요청으로 캐시를 일부만 채우지 않도록 DB에서는 최대 1,000개를 읽는다.
        List<MinuteCandleSnapshot> candles = readService.findRecent(assetId, intervalMinutes);
        if (!candles.isEmpty()) {
            cache.fillMissing(assetId, intervalMinutes, candles);
        }
        return List.copyOf(candles.subList(Math.max(0, candles.size() - limit), candles.size()));
    }
}
