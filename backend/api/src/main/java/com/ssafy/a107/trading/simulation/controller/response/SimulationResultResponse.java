package com.ssafy.a107.trading.simulation.controller.response;

import com.ssafy.a107.trading.simulation.domain.SimulationResult;

import java.math.BigDecimal;
import java.time.Instant;

public record SimulationResultResponse(Instant asOf, BigDecimal assetValue, BigDecimal cumulativeReturn,
                                       BigDecimal mdd, BigDecimal sharpeRatio, BigDecimal volatility,
                                       BigDecimal winRate, int tradeCount, BigDecimal totalFee) {
    public static SimulationResultResponse from(SimulationResult result) {
        return new SimulationResultResponse(result.getAsOf(), result.getAssetValue(), result.getCumulativeReturn(),
                result.getMdd(), result.getSharpeRatio(), result.getVolatility(), result.getWinRate(),
                result.getTradeCount(), result.getTotalFee());
    }
}
