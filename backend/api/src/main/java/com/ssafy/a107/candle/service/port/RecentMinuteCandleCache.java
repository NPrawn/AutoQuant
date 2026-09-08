package com.ssafy.a107.candle.service.port;

import com.ssafy.a107.candle.domain.MinuteCandleSnapshot;

import java.util.List;
import java.util.UUID;

public interface RecentMinuteCandleCache {

    int CAPACITY = 1000;

    // 시간 오름차순으로 반환한다. 캐시 장애/손상은 miss로 처리한다.
    List<MinuteCandleSnapshot> findRecent(UUID assetId, int intervalMinutes, int limit);

    // 기존 값을 덮어쓰지 않고 빈 시간대만 보충한다. 쓰기 실패는 조회에 영향을 주지 않는다.
    void fillMissing(UUID assetId, int intervalMinutes, List<MinuteCandleSnapshot> candles);
}
