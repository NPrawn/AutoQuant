package com.ssafy.a107.trading.backtest.engine.execution;

import com.ssafy.a107.trading.backtest.domain.TradeSide;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class OrderPrecisionCalculatorTest {

    private final OrderPrecisionCalculator calculator = new OrderPrecisionCalculator();

    @DisplayName("매수 가격은 가격 주문 단위로 올림한다")
    @Test
    void roundBuyPriceUpToTickSize() {
        BigDecimal roundedPrice = calculator.roundPrice(
                BigDecimal.valueOf(100_050),
                BigDecimal.valueOf(1_000),
                TradeSide.BUY
        );

        assertThat(roundedPrice).isEqualByComparingTo("101000");
    }

    @DisplayName("매도 가격은 가격 주문 단위로 내림한다")
    @Test
    void roundSellPriceDownToTickSize() {
        BigDecimal roundedPrice = calculator.roundPrice(
                BigDecimal.valueOf(100_050),
                BigDecimal.valueOf(1_000),
                TradeSide.SELL
        );

        assertThat(roundedPrice).isEqualByComparingTo("100000");
    }

    @DisplayName("체결 수량은 수량 주문 단위로 내림한다")
    @Test
    void roundQuantityDownToStepSize() {
        BigDecimal roundedQuantity = calculator.roundQuantity(
                new BigDecimal("1.23456789"),
                new BigDecimal("0.0001")
        );

        assertThat(roundedQuantity).isEqualByComparingTo("1.2345");
    }

    @DisplayName("수량이 주문 단위보다 작으면 0으로 내림한다")
    @Test
    void roundQuantityBelowStepSizeToZero() {
        BigDecimal roundedQuantity = calculator.roundQuantity(
                new BigDecimal("0.00001"),
                new BigDecimal("0.0001")
        );

        assertThat(roundedQuantity).isZero();
    }

    @DisplayName("가격은 0보다 커야 한다")
    @Test
    void rejectZeroPrice() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> calculator.roundPrice(
                        BigDecimal.ZERO,
                        BigDecimal.ONE,
                        TradeSide.BUY
                ));
    }

    @DisplayName("수량 주문 단위는 0보다 커야 한다")
    @Test
    void rejectZeroQuantityStepSize() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> calculator.roundQuantity(
                        BigDecimal.ONE,
                        BigDecimal.ZERO
                ));
    }

    @DisplayName("주문 단위 적용 후 매도 가격은 0보다 커야 한다")
    @Test
    void rejectRoundedSellPriceOfZero() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> calculator.roundPrice(
                        BigDecimal.valueOf(500),
                        BigDecimal.valueOf(1_000),
                        TradeSide.SELL
                ));
    }
}
