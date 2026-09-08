package com.ssafy.a107.realtime.infrastructure.redis;

final class RealtimeRedisChannels {

    static final String TRADE = "trade:realtime:updates";
    static final String LIVE_MINUTE_CANDLE = "candle:1m:live:updates";

    private RealtimeRedisChannels() {
    }
}
