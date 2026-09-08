package com.ssafy.a107.trading.backtest.controller.response;

import com.ssafy.a107.trading.backtest.domain.BacktestEquity;
import org.springframework.data.domain.Page;

import java.util.List;

public record BacktestEquityPageResponse(
        List<BacktestEquityResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public static BacktestEquityPageResponse from(Page<BacktestEquity> equities) {
        return new BacktestEquityPageResponse(
                equities.getContent().stream()
                        .map(BacktestEquityResponse::from)
                        .toList(),
                equities.getNumber(),
                equities.getSize(),
                equities.getTotalElements(),
                equities.getTotalPages()
        );
    }
}
