package com.ssafy.a107.strategy.domain.dsl;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum Timeframe {
    ONE_HOUR("1H"),
    FOUR_HOURS("4H"),
    ONE_DAY("1D");

    private final String value;

    Timeframe(String value) {
        this.value = value;
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
}
