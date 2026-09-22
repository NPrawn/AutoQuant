package com.ssafy.a107.strategy.engine.factor;

import com.ssafy.a107.candle.domain.MinuteCandle;
import com.ssafy.a107.candle.domain.MinuteCandleFixture;
import com.ssafy.a107.strategy.domain.dsl.FactorOperand;
import com.ssafy.a107.strategy.domain.dsl.FactorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class Ta4jFactorCalculatorTest {

    private final Ta4jFactorCalculator calculator = new Ta4jFactorCalculator();

    @Test
    @DisplayName("N기간 수익률을 백분율로 계산한다")
    void calculateReturn() {
        // given
        List<MinuteCandle> candles = candles(new String[]{"100", "110", "121"}, new String[]{"1", "1", "1"});

        // when
        BigDecimal result = calculator.calculate(FactorType.RETURN, 2, candles).orElseThrow();

        // then
        assertThat(result).isEqualByComparingTo("21");
    }

    @Test
    @DisplayName("RSI를 Wilder 방식으로 계산한다")
    void calculateRsi() {
        // given
        List<MinuteCandle> candles = candles(new String[]{"100", "101", "102"}, new String[]{"1", "1", "1"});

        // when
        BigDecimal result = calculator.calculate(FactorType.RSI, 2, candles).orElseThrow();

        // then
        assertThat(result).isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("현재 종가의 SMA 괴리율을 백분율로 계산한다")
    void calculateSmaDisparity() {
        // given
        List<MinuteCandle> candles = candles(new String[]{"10", "20", "30"}, new String[]{"1", "1", "1"});

        // when
        BigDecimal result = calculator.calculate(FactorType.SMA_DISPARITY, 3, candles).orElseThrow();

        // then
        assertThat(result).isEqualByComparingTo("50");
    }

    @Test
    @DisplayName("최근 N개 1캔들 수익률의 모집단 표준편차를 계산한다")
    void calculateVolatility() {
        // given
        List<MinuteCandle> candles = candles(new String[]{"100", "110", "110"}, new String[]{"1", "1", "1"});

        // when
        BigDecimal result = calculator.calculate(FactorType.VOLATILITY, 2, candles).orElseThrow();

        // then
        assertThat(result).isEqualByComparingTo("5");
    }

    @Test
    @DisplayName("현재 거래량의 평균 거래량 대비 비율을 계산한다")
    void calculateVolumeRatio() {
        // given
        List<MinuteCandle> candles = candles(new String[]{"1", "1", "1"}, new String[]{"10", "20", "30"});

        // when
        BigDecimal result = calculator.calculate(FactorType.VOLUME_RATIO, 3, candles).orElseThrow();

        // then
        assertThat(result).isEqualByComparingTo("1.5");
    }

    @Test
    @DisplayName("최근 N개 종가 최고점 대비 현재 하락률을 계산한다")
    void calculateDrawdown() {
        // given
        List<MinuteCandle> candles = candles(new String[]{"100", "120", "90"}, new String[]{"1", "1", "1"});

        // when
        BigDecimal result = calculator.calculate(FactorType.DRAWDOWN, 3, candles).orElseThrow();

        // then
        assertThat(result).isEqualByComparingTo("-25");
    }

    @Test
    @DisplayName("여러 Factor는 하나의 캔들 시계열에서 함께 계산한다")
    void calculateAllFactorsFromOneCandleSeries() {
        // given
        FactorOperand returnFactor = new FactorOperand(FactorType.RETURN, 2);
        FactorOperand volatilityFactor = new FactorOperand(FactorType.VOLATILITY, 2);
        List<MinuteCandle> candles = candles(
                new String[]{"100", "110", "110"},
                new String[]{"1", "1", "1"}
        );

        // when
        Map<FactorOperand, BigDecimal> factorValues = calculator.calculateAll(
                Set.of(returnFactor, volatilityFactor),
                candles
        );

        // then
        assertThat(factorValues.get(returnFactor)).isEqualByComparingTo("10");
        assertThat(factorValues.get(volatilityFactor)).isEqualByComparingTo("5");
    }

    @Test
    @DisplayName("warm-up 데이터가 부족하면 Factor 값을 제공하지 않는다")
    void returnEmptyWhenWarmUpDataIsInsufficient() {
        // given
        List<MinuteCandle> candles = candles(new String[]{"100", "101"}, new String[]{"1", "1"});

        // when
        var result = calculator.calculate(FactorType.RSI, 2, candles);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("period가 양수가 아니면 계산을 거부한다")
    void rejectNonPositivePeriod() {
        // given
        List<MinuteCandle> candles = candles(new String[]{"100"}, new String[]{"1"});

        // when & then
        assertThatIllegalArgumentException()
                .isThrownBy(() -> calculator.calculate(FactorType.RSI, 0, candles))
                .withMessage("period must be positive");
    }

    @Test
    @DisplayName("캔들이 시간 오름차순이 아니면 계산을 거부한다")
    void rejectUnorderedCandles() {
        // given
        UUID assetId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        List<MinuteCandle> candles = List.of(
                MinuteCandleFixture.minuteCandle(assetId, 1, Instant.parse("2026-01-01T00:01:00Z"), new BigDecimal("100"), BigDecimal.ONE),
                MinuteCandleFixture.minuteCandle(assetId, 1, Instant.parse("2026-01-01T00:00:00Z"), new BigDecimal("101"), BigDecimal.ONE)
        );

        // when & then
        assertThatIllegalArgumentException()
                .isThrownBy(() -> calculator.calculate(FactorType.RSI, 1, candles))
                .withMessage("candles must be ordered by ascending openTime");
    }

    private List<MinuteCandle> candles(String[] closes, String[] volumes) {
        UUID assetId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        Instant firstOpenTime = Instant.parse("2026-01-01T00:00:00Z");

        return java.util.stream.IntStream.range(0, closes.length)
                .mapToObj(index -> MinuteCandleFixture.minuteCandle(
                        assetId,
                        1,
                        firstOpenTime.plusSeconds(index * 60L),
                        new BigDecimal(closes[index]),
                        new BigDecimal(volumes[index])
                ))
                .toList();
    }
}
