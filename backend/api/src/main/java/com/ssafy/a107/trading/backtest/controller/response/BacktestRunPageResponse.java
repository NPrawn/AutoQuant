package com.ssafy.a107.trading.backtest.controller.response;

import com.ssafy.a107.trading.backtest.domain.BacktestRun;
import org.springframework.data.domain.Page;

import java.util.List;

public record BacktestRunPageResponse(
        List<BacktestRunResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public static BacktestRunPageResponse from(Page<BacktestRun> backtestRuns) {
        return new BacktestRunPageResponse(
                backtestRuns.getContent().stream()
                        .map(BacktestRunResponse::from)
                        .toList(),
                backtestRuns.getNumber(),
                backtestRuns.getSize(),
                backtestRuns.getTotalElements(),
                backtestRuns.getTotalPages()
        );
    }
}
