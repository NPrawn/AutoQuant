package com.ssafy.a107.strategy.controller.response;

import com.ssafy.a107.strategy.domain.Strategy;
import com.ssafy.a107.strategy.domain.dsl.StrategyDsl;

import java.time.Instant;

public record StrategyResponse(
        Long strategyId,
        String name,
        String description,
        StrategyDsl strategyDsl,
        Instant createdAt
) {

    public static StrategyResponse from(Strategy strategy) {
        return new StrategyResponse(
                strategy.getStrategyId(),
                strategy.getName(),
                strategy.getDescription(),
                strategy.getStrategyDsl(),
                strategy.getCreatedAt()
        );
    }
}
