package com.ssafy.a107.trading.simulation.controller.response;

import com.ssafy.a107.trading.simulation.domain.SimulationEquity;

import java.math.BigDecimal;
import java.time.Instant;

public record SimulationEquityResponse(Instant valuationAt, BigDecimal totalAssetValue, BigDecimal cashBalance,
                                       BigDecimal returnRate, BigDecimal drawdownRate) {
    public static SimulationEquityResponse from(SimulationEquity equity) {
        return new SimulationEquityResponse(equity.getId().getValuationAt(), equity.getTotalAssetValue(),
                equity.getCashBalance(), equity.getReturnRate(), equity.getDrawdownRate());
    }
}
