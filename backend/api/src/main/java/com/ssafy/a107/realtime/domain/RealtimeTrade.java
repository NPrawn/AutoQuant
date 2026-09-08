package com.ssafy.a107.realtime.domain;

import java.util.UUID;

public record RealtimeTrade(
        UUID assetId, String marketCode, String price, String volume,
        String side, long tradedAtUnixMilli, long sequenceId
) {
}
