package com.ssafy.a107.candle.domain;

import com.ssafy.a107.common.exception.ApiErrorCode;
import com.ssafy.a107.common.exception.ApiException;

import java.util.List;

public final class CandleIntervals {

    public static final List<Integer> SUPPORTED = List.of(1, 2, 5, 15, 60, 240, 1440);

    private CandleIntervals() {
    }

    public static void requireSupported(int intervalMinutes) {
        if (!SUPPORTED.contains(intervalMinutes)) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST);
        }
    }
}
