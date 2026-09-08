package com.ssafy.a107.realtime.infrastructure.websocket;

import java.util.regex.Pattern;

final class RealtimeDestinations {

    private static final String PREFIX = "/topic/markets/";
    private static final Pattern SUBSCRIPTION =
            Pattern.compile("/topic/markets/KRW-[A-Z0-9]+/(?:trades|candles/1m)");

    private RealtimeDestinations() {
    }

    static String trades(String marketCode) {
        return PREFIX + marketCode + "/trades";
    }

    static String liveMinuteCandles(String marketCode) {
        return PREFIX + marketCode + "/candles/1m";
    }

    static boolean isAllowed(String destination) {
        return destination != null && SUBSCRIPTION.matcher(destination).matches();
    }
}
