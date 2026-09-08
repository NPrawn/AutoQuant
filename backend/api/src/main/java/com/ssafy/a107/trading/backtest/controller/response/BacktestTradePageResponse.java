package com.ssafy.a107.trading.backtest.controller.response;

import com.ssafy.a107.trading.backtest.domain.BacktestTrade;
import org.springframework.data.domain.Page;

import java.util.List;

public record BacktestTradePageResponse(
        List<BacktestTradeResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public static BacktestTradePageResponse from(Page<BacktestTrade> trades) {
        return new BacktestTradePageResponse(
                trades.getContent().stream()
                        .map(BacktestTradeResponse::from)
                        .toList(),
                trades.getNumber(),
                trades.getSize(),
                trades.getTotalElements(),
                trades.getTotalPages()
        );
    }
}
