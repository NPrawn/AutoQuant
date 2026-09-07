package com.ssafy.a107.trading.backtest.engine.execution;

import com.ssafy.a107.trading.backtest.domain.TradeSide;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class SlippagePriceCalculatorTest {

    private static final BigDecimal REFERENCE_PRICE = BigDecimal.valueOf(100_000);

    private final SlippagePriceCalculator calculator = new SlippagePriceCalculator();

    @DisplayName("매수 가격에는 슬리피지를 더한다")
    @Test
    void addSlippageToBuyPrice() {
        BigDecimal price = calculator.calculate(
                REFERENCE_PRICE,
                BigDecimal.valueOf(5),
                TradeSide.BUY
        );

        assertThat(price).isEqualByComparingTo("100050");
    }

    @DisplayName("매도 가격에는 슬리피지를 차감한다")
    @Test
    void subtractSlippageFromSellPrice() {
        BigDecimal price = calculator.calculate(
                REFERENCE_PRICE,
                BigDecimal.valueOf(5),
                TradeSide.SELL
        );

        assertThat(price).isEqualByComparingTo("99950");
    }

    @DisplayName("슬리피지가 0이면 기준 가격을 유지한다")
    @Test
    void preservePriceWhenSlippageIsZero() {
        BigDecimal price = calculator.calculate(
                REFERENCE_PRICE,
                BigDecimal.ZERO,
                TradeSide.BUY
        );

        assertThat(price).isEqualByComparingTo(REFERENCE_PRICE);
    }

    @DisplayName("기준 가격은 0보다 커야 한다")
    @Test
    void rejectZeroReferencePrice() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> calculator.calculate(
                        BigDecimal.ZERO,
                        BigDecimal.valueOf(5),
                        TradeSide.BUY
                ));
    }

    @DisplayName("슬리피지는 음수일 수 없다")
    @Test
    void rejectNegativeSlippageBps() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> calculator.calculate(
                        REFERENCE_PRICE,
                        BigDecimal.valueOf(-1),
                        TradeSide.BUY
                ));
    }

    @DisplayName("슬리피지는 10000 bps보다 작아야 한다")
    @Test
    void rejectSlippageBpsAtUpperBound() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> calculator.calculate(
                        REFERENCE_PRICE,
                        BigDecimal.valueOf(10_000),
                        TradeSide.SELL
                ));
    }
}
