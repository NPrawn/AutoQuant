package com.ssafy.a107.strategy.controller.response;

import com.ssafy.a107.strategy.domain.Strategy;
import com.ssafy.a107.strategy.domain.dsl.StrategyDsl;

import java.time.Instant;

public record StrategyResponse(
        Long strategyId,
        /* 이 판이 속한 전략의 뿌리. 뿌리 자신은 자기 번호다. 화면은 이 값으로 판들을 한 전략으로 묶는다. */
        Long rootStrategyId,
        int versionNo,
        String name,
        String description,
        StrategyDsl strategyDsl,
        boolean published,
        Instant createdAt
) {

    public static StrategyResponse from(Strategy strategy) {
        return new StrategyResponse(
                strategy.getStrategyId(),
                strategy.getRootStrategyId(),
                strategy.getVersionNo(),
                strategy.getName(),
                strategy.getDescription(),
                strategy.getStrategyDsl(),
                strategy.isPublished(),
                strategy.getCreatedAt()
        );
    }
}
