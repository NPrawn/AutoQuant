package com.ssafy.a107.ai.service.port;

import com.ssafy.a107.strategy.domain.dsl.StrategyDsl;

public interface AiStrategyTranslator {

    AiStrategyTranslation translate(String userInput, StrategyDsl currentStrategy);
}
