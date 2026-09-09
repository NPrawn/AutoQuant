package com.ssafy.a107.trading.simulation.service;

import com.ssafy.a107.realtime.domain.LiveMinuteCandle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class SimulationLiveCandleListener {

    private static final Logger log = LoggerFactory.getLogger(SimulationLiveCandleListener.class);

    private final SimulationCandleExecutionService simulationCandleExecutionService;

    public SimulationLiveCandleListener(SimulationCandleExecutionService simulationCandleExecutionService) {
        this.simulationCandleExecutionService = simulationCandleExecutionService;
    }

    @EventListener
    public void onLiveMinuteCandle(LiveMinuteCandle candle) {
        try {
            simulationCandleExecutionService.handle(candle);
        } catch (RuntimeException exception) {
            log.warn("Failed to process simulation candle: {}", exception.getClass().getSimpleName());
        }
    }
}
