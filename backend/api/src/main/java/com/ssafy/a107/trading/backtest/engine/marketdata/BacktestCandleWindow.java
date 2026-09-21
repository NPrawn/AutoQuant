package com.ssafy.a107.trading.backtest.engine.marketdata;

import com.ssafy.a107.candle.domain.MinuteCandle;
import com.ssafy.a107.strategy.domain.dsl.Timeframe;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

public record BacktestCandleWindow(
        Timeframe timeframe,
        Instant evaluationStart,
        Instant evaluationEnd,
        Map<UUID, List<MinuteCandle>> candlesByAssetId
) {

    public BacktestCandleWindow {
        timeframe = Objects.requireNonNull(timeframe);
        evaluationStart = Objects.requireNonNull(evaluationStart);
        evaluationEnd = Objects.requireNonNull(evaluationEnd);
        candlesByAssetId = copyAndValidate(
                timeframe,
                evaluationEnd,
                candlesByAssetId
        );

        if (!evaluationStart.isBefore(evaluationEnd)) {
            throw new IllegalArgumentException(
                    "Evaluation start must be before end"
            );
        }
    }

    public Map<UUID, List<MinuteCandle>> warmupCandlesByAssetId() {
        return filterByOpenTime(candleOpenTime -> candleOpenTime.isBefore(evaluationStart));
    }

    /*
     * 평가 봉은 평가 구간 안에서 열리고 닫히는 봉이다. 기간 끝이 봉 경계와 어긋나면 마지막 봉이 구간을 넘겨
     * 닫히는데, 그 봉으로 판단하면 요청한 기간 밖의 정보를 쓰게 된다. BacktestStrategyExecutionContextFactory
     * 도 같은 이유로 구간 밖에서 닫히는 봉을 거절하므로, 여기서 걸러야 그 예외를 만나지 않는다.
     */
    public Map<UUID, List<MinuteCandle>> evaluationCandlesByAssetId() {
        Instant lastEvaluationCandleOpenTime = evaluationEnd.minus(
                Duration.ofMinutes(timeframe.getIntervalMinutes())
        );
        return filterByOpenTime(candleOpenTime ->
                !candleOpenTime.isBefore(evaluationStart)
                        && !candleOpenTime.isAfter(lastEvaluationCandleOpenTime));
    }

    private Map<UUID, List<MinuteCandle>> filterByOpenTime(
            java.util.function.Predicate<Instant> predicate
    ) {
        Map<UUID, List<MinuteCandle>> filteredCandlesByAssetId = new LinkedHashMap<>();

        for (Map.Entry<UUID, List<MinuteCandle>> entry : candlesByAssetId.entrySet()) {
            List<MinuteCandle> candles = entry.getValue().stream()
                    .filter(candle -> predicate.test(candle.getId().getOpenTime()))
                    .toList();
            filteredCandlesByAssetId.put(entry.getKey(), candles);
        }

        return Collections.unmodifiableMap(filteredCandlesByAssetId);
    }

    private static Map<UUID, List<MinuteCandle>> copyAndValidate(
            Timeframe timeframe,
            Instant evaluationEnd,
            Map<UUID, List<MinuteCandle>> candlesByAssetId
    ) {
        Objects.requireNonNull(candlesByAssetId);

        Map<UUID, List<MinuteCandle>> copiedCandlesByAssetId = new LinkedHashMap<>();

        for (Map.Entry<UUID, List<MinuteCandle>> entry : candlesByAssetId.entrySet()) {
            UUID assetId = Objects.requireNonNull(entry.getKey());
            List<MinuteCandle> candles = List.copyOf(entry.getValue());

            validateCandles(assetId, timeframe, evaluationEnd, candles);
            copiedCandlesByAssetId.put(assetId, candles);
        }

        return Collections.unmodifiableMap(copiedCandlesByAssetId);
    }

    /* BacktestCandleWindowProvider 의 적재 상한과 같은 값이다. 한쪽을 바꾸면 다른 쪽도 바꾼다. */
    private static Instant executionCandleBound(Timeframe timeframe, Instant evaluationEnd) {
        return evaluationEnd.plus(Duration.ofMinutes(timeframe.getIntervalMinutes()));
    }

    private static void validateCandles(
            UUID assetId,
            Timeframe timeframe,
            Instant evaluationEnd,
            List<MinuteCandle> candles
    ) {
        Instant previousOpenTime = null;

        for (MinuteCandle candle : candles) {
            if (!assetId.equals(candle.getId().getAssetId())) {
                throw new IllegalArgumentException(
                        "Candle asset must match map key"
                );
            }

            if (candle.getId().getIntervalMinutes() != timeframe.getIntervalMinutes()) {
                throw new IllegalArgumentException(
                        "Candle interval must match timeframe"
                );
            }

            /*
             * 상한은 평가 종료가 아니라 적재 상한(평가 종료 + 봉 하나)이다. 마지막 신호 봉의 체결 봉은
             * 평가 종료 뒤에 열리고, 기간이 봉 경계에 맞지 않으면 그 봉의 open time 이 평가 종료를 넘는다.
             * 체결 봉은 evaluationCandlesByAssetId 가 평가 봉에서 걸러 낸다.
             */
            Instant openTime = candle.getId().getOpenTime();
            if (!openTime.isBefore(executionCandleBound(timeframe, evaluationEnd))) {
                throw new IllegalArgumentException(
                        "Candle open time must be before one candle after evaluation end"
                );
            }

            if (previousOpenTime != null && !previousOpenTime.isBefore(openTime)) {
                throw new IllegalArgumentException(
                        "Candles must be ordered by ascending open time"
                );
            }

            previousOpenTime = openTime;
        }
    }
}
