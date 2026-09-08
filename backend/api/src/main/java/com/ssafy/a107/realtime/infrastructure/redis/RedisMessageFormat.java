package com.ssafy.a107.realtime.infrastructure.redis;

final class RedisMessageFormat {

    static final String UUID_PATTERN = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";
    static final String MARKET_CODE_PATTERN = "KRW-[A-Z0-9]+";

    private RedisMessageFormat() {
    }
}
