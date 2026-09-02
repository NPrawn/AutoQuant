package com.ssafy.a107.strategy.domain.dsl;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum Timeframe {
    ONE_HOUR("1H", 60),
    FOUR_HOURS("4H", 240),
    ONE_DAY("1D", 1_440);

    private final String value;
    private final int intervalMinutes;

    Timeframe(String value, int intervalMinutes) {
        this.value = value;
        this.intervalMinutes = intervalMinutes;
    }

    @JsonCreator
    public static Timeframe fromValue(String value) {
        return Arrays.stream(values())
                .filter(timeframe -> timeframe.value.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported timeframe: " + value));
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public int getIntervalMinutes() {
        return intervalMinutes;
    }
}
