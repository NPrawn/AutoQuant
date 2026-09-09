package com.ssafy.a107.realtime.controller.response;

import com.ssafy.a107.realtime.domain.RealtimeTrade;

import java.util.UUID;

public record RealtimeTradeResponse(
        UUID assetId, String marketCode, String price, String volume,
        String side, long tradedAtUnixMilli, long sequenceId
) {
    public static RealtimeTradeResponse from(RealtimeTrade trade) {
        return new RealtimeTradeResponse(trade.assetId(), trade.marketCode(), trade.price(), trade.volume(),
                trade.side(), trade.tradedAtUnixMilli(), trade.sequenceId());
    }
}
