package com.ssafy.a107.trading.backtest.engine.execution;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class SlippageAmountCalculatorTest {

    private final SlippageAmountCalculator calculator = new SlippageAmountCalculator();

    @DisplayName("매수 체결가 상승분으로 슬리피지 금액을 계산한다")
    @Test
    void calculateBuySlippageAmount() {
        BigDecimal amount = calculator.calculate(
                BigDecimal.valueOf(100_000),
                BigDecimal.valueOf(101_000),
                BigDecimal.valueOf(2)
        );

        assertThat(amount).isEqualByComparingTo("2000.00000000");
    }

    @DisplayName("매도 체결가 하락분으로 슬리피지 금액을 계산한다")
    @Test
    void calculateSellSlippageAmount() {
        BigDecimal amount = calculator.calculate(
                BigDecimal.valueOf(100_000),
                BigDecimal.valueOf(99_000),
                BigDecimal.valueOf(2)
        );

        assertThat(amount).isEqualByComparingTo("2000.00000000");
    }

    @DisplayName("기준가와 체결가가 같으면 슬리피지 금액은 0이다")
    @Test
    void returnZeroForSamePrice() {
        BigDecimal amount = calculator.calculate(
                BigDecimal.valueOf(100_000),
                BigDecimal.valueOf(100_000),
                BigDecimal.valueOf(2)
        );

        assertThat(amount).isZero();
        assertThat(amount.scale()).isEqualTo(8);
    }

    @DisplayName("슬리피지 금액은 소수점 8자리에서 반올림한다")
    @Test
    void roundSlippageAmountAtEightDecimalPlaces() {
        BigDecimal amount = calculator.calculate(
                BigDecimal.ONE,
                new BigDecimal("1.00000001"),
                new BigDecimal("0.5")
        );

        assertThat(amount).isEqualByComparingTo("0.00000001");
    }

    @DisplayName("기준 가격은 0보다 커야 한다")
    @Test
    void rejectZeroReferencePrice() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> calculator.calculate(
                        BigDecimal.ZERO,
                        BigDecimal.ONE,
                        BigDecimal.ONE
                ));
    }

    @DisplayName("체결 가격은 0보다 커야 한다")
    @Test
    void rejectZeroExecutedPrice() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> calculator.calculate(
                        BigDecimal.ONE,
                        BigDecimal.ZERO,
                        BigDecimal.ONE
                ));
    }

    @DisplayName("체결 수량은 0보다 커야 한다")
    @Test
    void rejectZeroQuantity() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> calculator.calculate(
                        BigDecimal.ONE,
                        BigDecimal.ONE,
                        BigDecimal.ZERO
                ));
    }
}
