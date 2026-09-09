package com.ssafy.a107.trading.simulation.service;

import com.ssafy.a107.realtime.domain.LiveMinuteCandle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

@Component
public class SimulationLiveCandleListener {

    private static final Logger log = LoggerFactory.getLogger(SimulationLiveCandleListener.class);

    private final SimulationCandleExecutionService simulationCandleExecutionService;
    private final TaskExecutor simulationExecutor;

    public SimulationLiveCandleListener(SimulationCandleExecutionService simulationCandleExecutionService,
                                        @Qualifier("simulationExecutor") TaskExecutor simulationExecutor) {
        this.simulationCandleExecutionService = simulationCandleExecutionService;
        this.simulationExecutor = simulationExecutor;
    }

    @EventListener
    public void onLiveMinuteCandle(LiveMinuteCandle candle) {
        simulationExecutor.execute(() -> {
            try {
                simulationCandleExecutionService.handle(candle);
            } catch (RuntimeException exception) {
                log.warn("Failed to process simulation candle: {}", exception.getClass().getSimpleName());
            }
        });
    }
}
