package com.ssafy.a107.realtime.infrastructure.websocket;

import java.util.regex.Pattern;

final class RealtimeDestinations {

    private static final String PREFIX = "/topic/markets/";
    private static final Pattern SUBSCRIPTION =
            Pattern.compile("/topic/markets/KRW-[A-Z0-9]+/(?:trades|candles/(?:1|2|5|15|60|240|1440)m)");

    private RealtimeDestinations() {
    }

    static String trades(String marketCode) {
        return PREFIX + marketCode + "/trades";
    }

    static String liveMinuteCandles(String marketCode, int intervalMinutes) {
        return PREFIX + marketCode + "/candles/" + intervalMinutes + "m";
    }

    static boolean isAllowed(String destination) {
        return destination != null && SUBSCRIPTION.matcher(destination).matches();
    }
}
