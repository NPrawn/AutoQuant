package com.ssafy.a107.trading.simulation.controller.response;

import com.ssafy.a107.trading.simulation.domain.SimulationTrade;
import org.springframework.data.domain.Page;

import java.util.List;

public record SimulationTradePageResponse(List<SimulationTradeResponse> content, int page, int size,
                                          long totalElements, int totalPages) {
    public static SimulationTradePageResponse from(Page<SimulationTrade> trades) {
        return new SimulationTradePageResponse(trades.getContent().stream().map(SimulationTradeResponse::from).toList(),
                trades.getNumber(), trades.getSize(), trades.getTotalElements(), trades.getTotalPages());
    }
}
