package com.ssafy.a107.realtime.infrastructure.redis;

import com.ssafy.a107.candle.domain.CandleIntervals;

import java.util.List;

final class RealtimeRedisChannels {

    static final String TRADE = "trade:realtime:updates";
    static final String LIVE_MINUTE_CANDLE = "candle:1m:live:updates";
    static final List<String> CANDLES = CandleIntervals.SUPPORTED.stream()
            .map(RealtimeRedisChannels::candle).toList();

    static String candle(int intervalMinutes) {
        return "candle:" + intervalMinutes + "m:live:updates";
    }

    private RealtimeRedisChannels() {
    }
}
