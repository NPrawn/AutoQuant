package com.ssafy.a107.trading.simulation.controller.response;

import com.ssafy.a107.trading.backtest.domain.PositionSide;
import com.ssafy.a107.trading.backtest.domain.TradeSide;
import com.ssafy.a107.trading.simulation.domain.SimulationTrade;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SimulationTradeResponse(UUID tradeId, UUID assetId, PositionSide positionSide, TradeSide side,
                                      Instant signalAt, Instant filledAt, BigDecimal executedPrice, BigDecimal quantity,
                                      BigDecimal notional, BigDecimal feeAmount, BigDecimal slippageAmount, String reason) {
    public static SimulationTradeResponse from(SimulationTrade trade) {
        return new SimulationTradeResponse(trade.getTradeId(), trade.getAsset().getAssetId(), trade.getPositionSide(),
                trade.getSide(), trade.getSignalAt(), trade.getFilledAt(), trade.getExecutedPrice(), trade.getQuantity(),
                trade.getNotional(), trade.getFeeAmount(), trade.getSlippageAmount(), trade.getReason());
    }
}
