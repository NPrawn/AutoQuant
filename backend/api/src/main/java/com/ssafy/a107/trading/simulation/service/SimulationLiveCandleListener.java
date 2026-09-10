package com.ssafy.a107.trading.simulation.service;

import com.ssafy.a107.realtime.domain.LiveMinuteCandle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SimulationLiveCandleListener {

    private static final Logger log = LoggerFactory.getLogger(SimulationLiveCandleListener.class);
    private static final Duration RETRY_DELAY = Duration.ofMillis(250);

    private final SimulationCandleExecutionService simulationCandleExecutionService;
    private final TaskExecutor simulationExecutor;
    private final TaskScheduler simulationRetryScheduler;
    private final Set<CandleKey> pendingCandles = ConcurrentHashMap.newKeySet();

    public SimulationLiveCandleListener(SimulationCandleExecutionService simulationCandleExecutionService,
                                        @Qualifier("simulationExecutor") TaskExecutor simulationExecutor,
                                        @Qualifier("simulationRetryScheduler") TaskScheduler simulationRetryScheduler) {
        this.simulationCandleExecutionService = simulationCandleExecutionService;
        this.simulationExecutor = simulationExecutor;
        this.simulationRetryScheduler = simulationRetryScheduler;
    }

    @EventListener
    public void onLiveMinuteCandle(LiveMinuteCandle candle) {
        if (!SimulationCandleExecutionService.supportsInterval(candle.intervalMinutes())) return;
        CandleKey candleKey = new CandleKey(candle.assetId(), candle.intervalMinutes(),
                Instant.ofEpochMilli(candle.openTimeUnixMilli()));
        pendingCandles.removeIf(pendingCandle -> pendingCandle.hasSameStreamAs(candleKey)
                && pendingCandle.candleAt().isBefore(candleKey.candleAt()));
        if (!pendingCandles.add(candleKey)) return;
        submit(candle, candleKey, 1);
    }

    private void submit(LiveMinuteCandle candle, CandleKey candleKey, int attempt) {
        if (!pendingCandles.contains(candleKey)) return;
        try {
            simulationExecutor.execute(() -> {
                boolean retryScheduled = false;
                try {
                    if (simulationCandleExecutionService.handle(candle)) {
                        retryScheduled = true;
                        scheduleRetry(candle, candleKey, attempt);
                        return;
                    }
                } catch (RuntimeException exception) {
                    log.warn("Failed to process simulation candle: assetId={}, intervalMinutes={}, candleAt={}, error={}",
                            candle.assetId(), candle.intervalMinutes(), candleKey.candleAt(), exception.getClass().getSimpleName());
                } finally {
                    if (!retryScheduled) {
                        pendingCandles.remove(candleKey);
                    }
                }
            });
        } catch (RuntimeException exception) {
            log.warn("Failed to enqueue simulation candle: assetId={}, intervalMinutes={}, candleAt={}, error={}",
                    candle.assetId(), candle.intervalMinutes(), candleKey.candleAt(), exception.getClass().getSimpleName());
            scheduleRetry(candle, candleKey, attempt);
        }
    }

    private void scheduleRetry(LiveMinuteCandle candle, CandleKey candleKey, int attempt) {
        try {
            simulationRetryScheduler.schedule(
                    () -> submit(candle, candleKey, attempt + 1),
                    Instant.now().plus(RETRY_DELAY.multipliedBy(1L << Math.min(attempt - 1, 6)))
            );
        } catch (RuntimeException exception) {
            pendingCandles.remove(candleKey);
            log.error("Failed to schedule simulation candle retry: assetId={}, intervalMinutes={}, candleAt={}, error={}",
                    candle.assetId(), candle.intervalMinutes(), candleKey.candleAt(), exception.getClass().getSimpleName());
        }
    }

    private record CandleKey(UUID assetId, int intervalMinutes, Instant candleAt) {

        private boolean hasSameStreamAs(CandleKey other) {
            return assetId.equals(other.assetId) && intervalMinutes == other.intervalMinutes;
        }
    }
}
