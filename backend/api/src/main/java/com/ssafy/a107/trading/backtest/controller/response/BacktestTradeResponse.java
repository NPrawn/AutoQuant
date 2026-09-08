package com.ssafy.a107.trading.backtest.controller.response;

import com.ssafy.a107.trading.backtest.domain.BacktestTrade;
import com.ssafy.a107.trading.backtest.domain.PositionSide;
import com.ssafy.a107.trading.backtest.domain.TradeSide;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BacktestTradeResponse(
        UUID tradeId,
        UUID assetId,
        String assetMarketCode,
        PositionSide positionSide,
        TradeSide side,
        Instant signalAt,
        Instant filledAt,
        BigDecimal executedPrice,
        BigDecimal quantity,
        BigDecimal notional,
        BigDecimal feeAmount,
        BigDecimal slippageAmount,
        String reason
) {

    public static BacktestTradeResponse from(BacktestTrade backtestTrade) {
        return new BacktestTradeResponse(
                backtestTrade.getTradeId(),
                backtestTrade.getAsset().getAssetId(),
                backtestTrade.getAsset().getExchangeMarketCode(),
                backtestTrade.getPositionSide(),
                backtestTrade.getSide(),
                backtestTrade.getSignalAt(),
                backtestTrade.getFilledAt(),
                backtestTrade.getExecutedPrice(),
                backtestTrade.getQuantity(),
                backtestTrade.getNotional(),
                backtestTrade.getFeeAmount(),
                backtestTrade.getSlippageAmount(),
                backtestTrade.getReason()
        );
    }
}
