package com.ssafy.a107.candle;

import com.ssafy.a107.candle.domain.MinuteCandleSnapshot;

import java.util.UUID;

public final class RecentMinuteCandleFixtures {

    public static final UUID ASSET_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    public static final long OPEN_TIME = 1800000000000L;
    public static final String GO_CACHE_VALUE = """
            {"asset_id":"11111111-1111-1111-1111-111111111111","interval_minutes":1,
             "open_time_unix_milli":1800000000000,"open_price":"100.123456789012",
             "high_price":"101","low_price":"99","close_price":"100",
             "trade_volume":"0.123456789012345678","trade_amount":"12.12345678",
             "last_event_time_unix_milli":1800000059999}
            """;

    private RecentMinuteCandleFixtures() {
    }

    public static MinuteCandleSnapshot candle(int minute) {
        return new MinuteCandleSnapshot(ASSET_ID, OPEN_TIME + minute * 60000L,
                "100.123456789012", "101", "99", "100",
                "0.123456789012345678", "12.12345678", OPEN_TIME + minute * 60000L + 59999L);
    }
}
