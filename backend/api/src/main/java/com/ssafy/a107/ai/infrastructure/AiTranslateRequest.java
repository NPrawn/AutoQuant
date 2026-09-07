package com.ssafy.a107.ai.infrastructure;

import com.ssafy.a107.strategy.domain.dsl.StrategyDsl;

record AiTranslateRequest(String userInput, StrategyDsl currentStrategy) {
}
