package com.ssafy.a107.strategy.engine.factor;

import com.ssafy.a107.candle.domain.MinuteCandle;
import com.ssafy.a107.candle.domain.MinuteCandleId;
import com.ssafy.a107.strategy.domain.dsl.FactorOperand;
import com.ssafy.a107.strategy.domain.dsl.FactorType;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.ROCIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.HighestValueIndicator;
import org.ta4j.core.indicators.helpers.PercentageChangeIndicator;
import org.ta4j.core.indicators.helpers.VolumeIndicator;
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator;
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.num.Num;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Component
public class Ta4jFactorCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public Optional<BigDecimal> calculate(FactorType factor, int period, List<MinuteCandle> candles) {
        validatePeriod(period);

        BarSeries series = toBarSeries(candles);
        return calculate(factor, period, series);
    }

    public Map<FactorOperand, BigDecimal> calculateAll(
            Set<FactorOperand> factorOperands,
            List<MinuteCandle> candles
    ) {
        Objects.requireNonNull(factorOperands);

        for (FactorOperand factorOperand : factorOperands) {
            Objects.requireNonNull(factorOperand);
            validatePeriod(factorOperand.period());
        }

        BarSeries series = toBarSeries(candles);
        if (series.isEmpty()) {
            return Map.of();
        }

        Map<FactorOperand, BigDecimal> factorValues = new LinkedHashMap<>();
        for (FactorOperand factorOperand : factorOperands) {
            calculate(factorOperand.factor(), factorOperand.period(), series)
                    .ifPresent(value -> factorValues.put(factorOperand, value));
        }
        return Map.copyOf(factorValues);
    }

    /**
     * 같은 캔들 시계열을 여러 시점에서 평가하는 백테스트용 계산기다.
     * 지표와 BarSeries는 한 번만 만들고, 각 시점에는 해당 시점까지 닫힌 캔들의 값만 읽는다.
     */
    public PreparedFactorSeries prepare(
            Set<FactorOperand> factorOperands,
            List<MinuteCandle> candles
    ) {
        Objects.requireNonNull(factorOperands);

        for (FactorOperand factorOperand : factorOperands) {
            Objects.requireNonNull(factorOperand);
            validatePeriod(factorOperand.period());
        }

        List<MinuteCandle> orderedCandles = List.copyOf(candles);
        BarSeries series = toBarSeries(orderedCandles);
        Map<FactorOperand, FactorValueResolver> resolvers = new LinkedHashMap<>();
        for (FactorOperand factorOperand : factorOperands) {
            resolvers.put(
                    factorOperand,
                    createResolver(factorOperand.factor(), factorOperand.period(), series)
            );
        }

        List<Instant> candleCloseTimes = orderedCandles.stream()
                .map(candle -> candle.getId().getOpenTime().plus(
                        Duration.ofMinutes(candle.getId().getIntervalMinutes())
                ))
                .toList();

        return new PreparedFactorSeries(candleCloseTimes, resolvers);
    }

    private Optional<BigDecimal> calculate(FactorType factor, int period, BarSeries series) {
        if (series.isEmpty()) {
            return Optional.empty();
        }

        return switch (factor) {
            case RETURN -> calculateReturn(series, period);
            case RSI -> calculateRsi(series, period);
            case SMA_DISPARITY -> calculateSmaDisparity(series, period);
            case VOLATILITY -> calculateVolatility(series, period);
            case VOLUME_RATIO -> calculateVolumeRatio(series, period);
            case DRAWDOWN -> calculateDrawdown(series, period);
        };
    }

    private Optional<BigDecimal> calculateReturn(BarSeries series, int period) {
        // N기간 수익률이다. 공식: ((현재 종가 / N기간 전 종가) - 1) * 100.
        // period는 달력 일수가 아니라 Strategy timeframe 기준 캔들 개수다.
        // 수익률 계산은 ta4j의 ROCIndicator에 위임한다.
        return calculateReturn(series, period, series.getEndIndex());
    }

    private Optional<BigDecimal> calculateReturn(BarSeries series, int period, int index) {
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        return indicatorValue(new ROCIndicator(close, period), index);
    }

    private Optional<BigDecimal> calculateRsi(BarSeries series, int period) {
        // RSI는 Welles Wilder 공식을 사용하는 ta4j RSIIndicator에 계산을 위임한다.
        // 4H 전략에서 period=20은 20개의 4시간봉을 뜻하며 20일 RSI가 아니다.
        // ta4j의 warm-up 구간 NaN은 아직 계산할 수 없는 값이므로 Optional.empty()로 반환한다.
        return calculateRsi(series, period, series.getEndIndex());
    }

    private Optional<BigDecimal> calculateRsi(BarSeries series, int period, int index) {
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        return indicatorValue(new RSIIndicator(close, period), index);
    }

    private Optional<BigDecimal> calculateSmaDisparity(BarSeries series, int period) {
        // SMA 괴리율은 현재 종가와 N개 캔들 단순이동평균의 백분율 차이다.
        // 공식: ((현재 종가 / SMA(종가, N)) - 1) * 100.
        // 이동평균은 ta4j SMAIndicator에 위임하고, DSL의 괴리율 공식은 여기서 직접 적용한다.
        return calculateSmaDisparity(series, period, series.getEndIndex());
    }

    private Optional<BigDecimal> calculateSmaDisparity(BarSeries series, int period, int index) {
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        Optional<BigDecimal> sma = indicatorValue(new SMAIndicator(close, period), index);
        return sma.filter(value -> value.signum() != 0)
                .map(value -> percentageDistance(close.getValue(index).bigDecimalValue(), value));
    }

    private Optional<BigDecimal> calculateVolatility(BarSeries series, int period) {
        // 변동성은 최근 N개 1캔들 단순 수익률(%)의 모집단 표준편차다.
        // 수익률은 ((종가_t / 종가_t-1) - 1) * 100이므로 N개의 수익률에는 N+1개의 종가가 필요하다.
        // 수익률과 모집단 표준편차 계산은 각각 ta4j PercentageChangeIndicator와 StandardDeviationIndicator에 위임한다.
        return calculateVolatility(series, period, series.getEndIndex());
    }

    private Optional<BigDecimal> calculateVolatility(BarSeries series, int period, int index) {
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        PercentageChangeIndicator returns = new PercentageChangeIndicator(close);
        return indicatorValue(StandardDeviationIndicator.ofPopulation(returns, period), index);
    }

    private Optional<BigDecimal> calculateVolumeRatio(BarSeries series, int period) {
        // 거래량 비율은 현재 캔들 거래량을 N개 캔들의 평균 거래량으로 나눈 값이다.
        // 공식: 현재 거래량 / SMA(거래량, N). 결과는 백분율이 아닌 비율이다.
        // ta4j VolumeIndicator의 period=1로 현재 거래량을 얻고 SMAIndicator에 평균 계산을 위임한다.
        return calculateVolumeRatio(series, period, series.getEndIndex());
    }

    private Optional<BigDecimal> calculateVolumeRatio(BarSeries series, int period, int index) {
        VolumeIndicator volume = new VolumeIndicator(series);
        Optional<BigDecimal> averageVolume = indicatorValue(new SMAIndicator(volume, period), index);
        return averageVolume.filter(value -> value.signum() != 0)
                .map(value -> volume.getValue(index).bigDecimalValue().divide(value, MathContext.DECIMAL128));
    }

    private Optional<BigDecimal> calculateDrawdown(BarSeries series, int period) {
        // 최근 N개 캔들 중 최고 종가 대비 현재 종가의 하락률이다.
        // 공식: ((현재 종가 / 최고 종가) - 1) * 100.
        // 최고 종가는 ta4j HighestValueIndicator에 위임한다. 이는 최악의 고점-저점 손실인 MDD가 아니다.
        return calculateDrawdown(series, period, series.getEndIndex());
    }

    private Optional<BigDecimal> calculateDrawdown(BarSeries series, int period, int index) {
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        Optional<BigDecimal> highestClose = indicatorValue(new HighestValueIndicator(close, period), index);
        return highestClose.filter(value -> value.signum() != 0)
                .map(value -> percentageDistance(close.getValue(index).bigDecimalValue(), value));
    }

    private Optional<BigDecimal> indicatorValue(Indicator<Num> indicator, BarSeries series) {
        return indicatorValue(indicator, series.getEndIndex());
    }

    private Optional<BigDecimal> indicatorValue(Indicator<Num> indicator, int index) {
        if (index < indicator.getCountOfUnstableBars()) {
            return Optional.empty();
        }

        Num value = indicator.getValue(index);
        if (Num.isNaNOrNull(value)) {
            return Optional.empty();
        }
        return Optional.of(value.bigDecimalValue());
    }

    private BigDecimal percentageDistance(BigDecimal value, BigDecimal reference) {
        return value.divide(reference, MathContext.DECIMAL128)
                .subtract(BigDecimal.ONE)
                .multiply(HUNDRED);
    }

    private FactorValueResolver createResolver(
            FactorType factor,
            int period,
            BarSeries series
    ) {
        return switch (factor) {
            case RETURN -> index -> calculateReturn(series, period, index);
            case RSI -> index -> calculateRsi(series, period, index);
            case SMA_DISPARITY -> index -> calculateSmaDisparity(series, period, index);
            case VOLATILITY -> index -> calculateVolatility(series, period, index);
            case VOLUME_RATIO -> index -> calculateVolumeRatio(series, period, index);
            case DRAWDOWN -> index -> calculateDrawdown(series, period, index);
        };
    }

    @FunctionalInterface
    private interface FactorValueResolver {
        Optional<BigDecimal> calculateAt(int index);
    }

    public static final class PreparedFactorSeries {

        private final List<Instant> candleCloseTimes;
        private final Map<FactorOperand, FactorValueResolver> resolvers;

        private PreparedFactorSeries(
                List<Instant> candleCloseTimes,
                Map<FactorOperand, FactorValueResolver> resolvers
        ) {
            this.candleCloseTimes = List.copyOf(candleCloseTimes);
            this.resolvers = Map.copyOf(resolvers);
        }

        public Map<FactorOperand, BigDecimal> calculateAt(Instant evaluationAt) {
            Objects.requireNonNull(evaluationAt);

            int index = Collections.binarySearch(candleCloseTimes, evaluationAt);
            if (index < 0) {
                index = -index - 2;
            }
            if (index < 0) {
                return Map.of();
            }

            Map<FactorOperand, BigDecimal> factorValues = new LinkedHashMap<>();
            for (Map.Entry<FactorOperand, FactorValueResolver> entry : resolvers.entrySet()) {
                entry.getValue().calculateAt(index)
                        .ifPresent(value -> factorValues.put(entry.getKey(), value));
            }
            return Map.copyOf(factorValues);
        }
    }

    private BarSeries toBarSeries(List<MinuteCandle> candles) {
        List<MinuteCandle> orderedCandles = List.copyOf(candles);
        validateSameCandleSeries(orderedCandles);
        validateChronologicalOrder(orderedCandles);

        List<Bar> bars = new ArrayList<>(orderedCandles.size());
        for (MinuteCandle candle : orderedCandles) {
            bars.add(toBar(candle));
        }
        return new BaseBarSeriesBuilder().withBars(bars).build();
    }

    private Bar toBar(MinuteCandle candle) {
        MinuteCandleId candleId = candle.getId();
        Duration interval = Duration.ofMinutes(candleId.getIntervalMinutes());
        Instant openTime = candleId.getOpenTime();
        return new BaseBar(
                interval,
                openTime,
                openTime.plus(interval),
                DecimalNum.valueOf(candle.getOpenPrice()),
                DecimalNum.valueOf(candle.getHighPrice()),
                DecimalNum.valueOf(candle.getLowPrice()),
                DecimalNum.valueOf(candle.getClosePrice()),
                DecimalNum.valueOf(candle.getTradeVolume()),
                DecimalNum.valueOf(candle.getTradeAmount()),
                0
        );
    }

    private void validatePeriod(int period) {
        if (period <= 0) {
            throw new IllegalArgumentException("period must be positive");
        }
    }

    private void validateSameCandleSeries(List<MinuteCandle> candles) {
        if (candles.isEmpty()) {
            return;
        }

        MinuteCandleId firstId = candles.getFirst().getId();
        UUID assetId = firstId.getAssetId();
        short intervalMinutes = firstId.getIntervalMinutes();
        for (MinuteCandle candle : candles) {
            MinuteCandleId candleId = candle.getId();
            if (!assetId.equals(candleId.getAssetId()) || intervalMinutes != candleId.getIntervalMinutes()) {
                throw new IllegalArgumentException("candles must belong to one asset and interval");
            }
        }
    }

    private void validateChronologicalOrder(List<MinuteCandle> candles) {
        for (int index = 1; index < candles.size(); index++) {
            Instant previousOpenTime = candles.get(index - 1).getId().getOpenTime();
            Instant currentOpenTime = candles.get(index).getId().getOpenTime();
            if (!previousOpenTime.isBefore(currentOpenTime)) {
                throw new IllegalArgumentException("candles must be ordered by ascending openTime");
            }
        }
    }
}
