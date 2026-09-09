package com.ssafy.a107.candle.controller.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record RecentMinuteCandlesRequest(@Min(1) @Max(1000) Integer limit) {

    public RecentMinuteCandlesRequest {
        if (limit == null) {
            limit = 1000;
        }
    }
}
