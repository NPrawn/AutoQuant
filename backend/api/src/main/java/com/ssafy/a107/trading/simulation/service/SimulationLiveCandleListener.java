package com.ssafy.a107.trading.simulation.service;

import com.ssafy.a107.realtime.domain.LiveMinuteCandle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SimulationLiveCandleListener {

    private static final Logger log = LoggerFactory.getLogger(SimulationLiveCandleListener.class);

    private final SimulationCandleExecutionService simulationCandleExecutionService;
    private final TaskExecutor simulationExecutor;
    private final Set<CandleKey> pendingCandles = ConcurrentHashMap.newKeySet();

    public SimulationLiveCandleListener(SimulationCandleExecutionService simulationCandleExecutionService,
                                        @Qualifier("simulationExecutor") TaskExecutor simulationExecutor) {
        this.simulationCandleExecutionService = simulationCandleExecutionService;
        this.simulationExecutor = simulationExecutor;
    }

    @EventListener
    public void onLiveMinuteCandle(LiveMinuteCandle candle) {
        CandleKey candleKey = new CandleKey(candle.assetId(), candle.intervalMinutes(),
                Instant.ofEpochMilli(candle.openTimeUnixMilli()));
        if (!pendingCandles.add(candleKey)) return;
        try {
            simulationExecutor.execute(() -> {
                try {
                    simulationCandleExecutionService.handle(candle);
                } catch (RuntimeException exception) {
                    log.warn("Failed to process simulation candle: assetId={}, intervalMinutes={}, candleAt={}, error={}",
                            candle.assetId(), candle.intervalMinutes(), candleKey.candleAt(), exception.getClass().getSimpleName());
                } finally {
                    pendingCandles.remove(candleKey);
                }
            });
        } catch (RuntimeException exception) {
            pendingCandles.remove(candleKey);
            log.warn("Failed to enqueue simulation candle: assetId={}, intervalMinutes={}, candleAt={}, error={}",
                    candle.assetId(), candle.intervalMinutes(), candleKey.candleAt(), exception.getClass().getSimpleName());
        }
    }

    private record CandleKey(UUID assetId, int intervalMinutes, Instant candleAt) {
    }
}
