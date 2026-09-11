package com.ssafy.a107.strategy.engine;

public class InsufficientCandleDataException extends StrategyExecutionException {

    public InsufficientCandleDataException(String message) {
        super(message);
    }
}
