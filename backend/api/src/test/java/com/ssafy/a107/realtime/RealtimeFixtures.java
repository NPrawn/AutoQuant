package com.ssafy.a107.realtime;

public final class RealtimeFixtures {

    public static final String TRADE = """
            {"asset_id":"11111111-1111-1111-1111-111111111111","market_code":"KRW-BTC",
             "price":"100000000.12345678","volume":"0.00000001","side":"BID",
             "traded_at_unix_milli":1800000000123,"sequence_id":123456789}
            """;
    public static final String CANDLE = """
            {"asset_id":"11111111-1111-1111-1111-111111111111","market_code":"KRW-BTC",
             "interval_minutes":1,"open_time_unix_milli":1800000000000,
             "open_price":"100000000.12345678","high_price":"100000001",
             "low_price":"99999999","close_price":"100000000",
             "trade_volume":"0.00000001","trade_amount":"1.0000000012345678",
             "last_event_time_unix_milli":1800000000123,"last_sequence_id":123456789}
            """;

    private RealtimeFixtures() {
    }
}
