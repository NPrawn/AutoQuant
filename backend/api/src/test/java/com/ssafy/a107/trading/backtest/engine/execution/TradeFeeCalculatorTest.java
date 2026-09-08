package com.ssafy.a107.trading.backtest.engine.execution;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class TradeFeeCalculatorTest {

    private final TradeFeeCalculator calculator = new TradeFeeCalculator();

    @DisplayName("체결 금액에 수수료율을 적용한다")
    @Test
    void calculateTradeFee() {
        BigDecimal fee = calculator.calculate(
                BigDecimal.valueOf(1_000_000),
                BigDecimal.valueOf(0.0005)
        );

        assertThat(fee).isEqualByComparingTo("500.00000000");
        assertThat(fee.scale()).isEqualTo(8);
    }

    @DisplayName("수수료는 소수점 8자리에서 반올림한다")
    @Test
    void roundFeeAtEightDecimalPlaces() {
        BigDecimal fee = calculator.calculate(
                new BigDecimal("0.00000003"),
                new BigDecimal("0.5")
        );

        assertThat(fee).isEqualByComparingTo("0.00000002");
    }

    @DisplayName("체결 금액이 0이면 수수료도 0이다")
    @Test
    void returnZeroForZeroNotional() {
        BigDecimal fee = calculator.calculate(
                BigDecimal.ZERO,
                BigDecimal.valueOf(0.0005)
        );

        assertThat(fee).isZero();
        assertThat(fee.scale()).isEqualTo(8);
    }

    @DisplayName("체결 금액은 음수일 수 없다")
    @Test
    void rejectNegativeNotional() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> calculator.calculate(
                        BigDecimal.valueOf(-1),
                        BigDecimal.valueOf(0.0005)
                ));
    }

    @DisplayName("수수료율은 음수일 수 없다")
    @Test
    void rejectNegativeFeeRate() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> calculator.calculate(
                        BigDecimal.valueOf(1_000_000),
                        BigDecimal.valueOf(-0.0005)
                ));
    }
}
