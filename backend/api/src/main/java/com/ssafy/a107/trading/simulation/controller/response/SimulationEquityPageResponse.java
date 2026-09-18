package com.ssafy.a107.trading.simulation.controller.response;

import com.ssafy.a107.trading.simulation.domain.SimulationEquity;
import org.springframework.data.domain.Page;

import java.util.List;

public record SimulationEquityPageResponse(List<SimulationEquityResponse> content, int page, int size,
                                           long totalElements, int totalPages) {
    public static SimulationEquityPageResponse from(Page<SimulationEquity> equities) {
        return new SimulationEquityPageResponse(equities.getContent().stream().map(SimulationEquityResponse::from).toList(),
                equities.getNumber(), equities.getSize(), equities.getTotalElements(), equities.getTotalPages());
    }
}
