package com.ssafy.a107.trading.simulation.controller.response;

import com.ssafy.a107.trading.backtest.domain.PositionSide;
import com.ssafy.a107.trading.simulation.domain.SimulationPosition;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SimulationPositionResponse(UUID assetId, PositionSide positionSide, BigDecimal quantity,
                                         BigDecimal averageEntryPrice, Instant updatedAt) {
    public static SimulationPositionResponse from(SimulationPosition position) {
        return new SimulationPositionResponse(position.getAsset().getAssetId(), position.getId().getPositionSide(),
                position.getQuantity(), position.getAverageEntryPrice(), position.getUpdatedAt());
    }
}
