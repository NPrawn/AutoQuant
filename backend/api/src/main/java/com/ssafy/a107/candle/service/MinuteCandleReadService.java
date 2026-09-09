package com.ssafy.a107.candle.service;

import com.ssafy.a107.candle.domain.MinuteCandleSnapshot;
import com.ssafy.a107.candle.repository.MinuteCandleRepository;
import com.ssafy.a107.candle.service.port.RecentMinuteCandleCache;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class MinuteCandleReadService {

    private final MinuteCandleRepository repository;

    public MinuteCandleReadService(MinuteCandleRepository repository) {
        this.repository = repository;
    }

    public List<MinuteCandleSnapshot> findRecent(UUID assetId, int intervalMinutes) {
        return repository.findByIdAssetIdAndIdIntervalMinutesOrderByIdOpenTimeDesc(
                        assetId, (short) intervalMinutes, PageRequest.of(0, RecentMinuteCandleCache.CAPACITY))
                .reversed().stream()
                .map(MinuteCandleSnapshot::from)
                .toList();
    }
}
